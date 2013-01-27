package jenkins.model;

import hudson.triggers.Trigger;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.Timer;
import java.util.Map.Entry;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.io.Closeables;
import com.google.common.io.Files;

/**
 * Cache that aims to re-use expensive computations
 *
 * @author Justin Santa Barbara
 *
 */
public class Caching {
    // TODO: Reduce for production
    private static final int SAVE_INTERVAL = 10 * 1000;

    private final static Caching INSTANCE = new Caching();

    public static Caching getCacheFor(Object o) {
        return INSTANCE;
    }

    final Cache<String, byte[]> cache = CacheBuilder.newBuilder().build();
    final File persist;

    boolean dirty;

    public Caching() {
        this.persist = new File(Jenkins.getInstance().getRootDir(), "cache.dat");
        tryLoad(persist);

        Timer timer = Trigger.timer;
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                if (dirty) {
                    LOGGER.log(Level.FINE, "Persisting cache");
                    System.out.println("Trying save to " + persist);
                    trySave();
                }
            }
        }, SAVE_INTERVAL);
    }

    public void trySave() {
        File tempFile = null;
        try {
            tempFile = File.createTempFile("cache", "tmp");
            save(tempFile);
            if (!tempFile.renameTo(persist)) {
                throw new IOException("Unable to rename file: " + tempFile
                        + " => " + persist);
            }

            tempFile = null;
            dirty = false;

            System.out.println("Saved: " + persist);
        } catch (Exception e) {
            e.printStackTrace();
            LOGGER.log(Level.WARNING, "Error persisting cache", e);
        } finally {
            if (tempFile != null) {
                tempFile.delete();
            }
        }
    }

    private void tryLoad(File file) {
        if (file.exists()) {
            DataInputStream dis = null;
            try {
                dis = new DataInputStream(new FileInputStream(file));

                while (true) {
                    // Note that expiration is currently just a placeholder
                    int expiration = dis.readInt();
                    if (expiration == 0)
                        break;
                    String key = dis.readUTF();
                    int valueLength = dis.readInt();
                    byte[] value = new byte[valueLength];
                    dis.readFully(value);
                    cache.put(key, value);
                }

            } catch (IOException e) {
                LOGGER.log(Level.WARNING, "Error reloading saved cache", e);
            } finally {
                Closeables.closeQuietly(dis);
            }
        }
    }

    private void save(File file) throws IOException {
        DataOutputStream dos = null;
        try {
            dos = new DataOutputStream(new FileOutputStream(file));

            ConcurrentMap<String, byte[]> map = cache.asMap();
            for (Entry<String, byte[]> entry : map.entrySet()) {
                // Note that expiration is currently just a placeholder

                String key = entry.getKey();
                byte[] value = entry.getValue();

                dos.writeInt(1);
                dos.writeUTF(key);
                dos.writeInt(value.length);
                dos.write(value);
            }
            dos.writeInt(0);
        } finally {
            Closeables.closeQuietly(dos);
        }
    }

    public <T> T getIfPresent(String cacheKey, Class<T> clazz) {
        byte[] data = cache.getIfPresent(cacheKey);
        if (data == null) {
            return null;
        }

        try {
            ObjectInputStream ois = new ObjectInputStream(
                    new ByteArrayInputStream(data));
            Object o = ois.readObject();
            if (clazz.isInstance(o)) {
                return (T) o;
            } else {
                LOGGER.log(Level.WARNING, "Cache class mismatch");
            }
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Error reading cache", e);
        } catch (ClassNotFoundException e) {
            LOGGER.log(Level.WARNING, "Error reading cache", e);
        }

        return null;
    }

    public <T extends Serializable> void put(String cacheKey, T item) {
        byte[] data = null;

        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ObjectOutputStream oos = new ObjectOutputStream(baos);
            oos.writeObject(item);
            oos.close();

            data = baos.toByteArray();
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Error persisting to cache", e);
        }

        if (data != null) {
            cache.put(cacheKey, data);
            dirty = true;
        }
    }

    private final static Logger LOGGER = Logger.getLogger(Caching.class
            .getName());

}
