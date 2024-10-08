package DirectoryWatcher;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.*;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static java.nio.file.StandardWatchEventKinds.*;

public class DirectoryWatcher implements Runnable, Service {

    private static final Logger LOGGER = LoggerFactory.getLogger(DirectoryWatcher.class);

    public enum Event {
        ENTRY_CREATE,
        ENTRY_MODIFY,
        ENTRY_DELETE
    }

    private static final Map<WatchEvent.Kind<Path>, Event> EVENT_MAP =
            new HashMap<WatchEvent.Kind<Path>, Event>() {{
                put(ENTRY_CREATE, Event.ENTRY_CREATE);
                put(ENTRY_MODIFY, Event.ENTRY_MODIFY);
                put(ENTRY_DELETE, Event.ENTRY_DELETE);
            }};

    private ExecutorService mExecutor;
    private Future<?> mWatcherTask;

    private final Set<Path> mWatched;
    private final boolean mPreExistingAsCreated;
    private final Listener mListener;
    private final Filter<Path> mFilter;

    public DirectoryWatcher(Builder builder) {
        mWatched = builder.mWatched;
        mPreExistingAsCreated = builder.mPreExistingAsCreated;
        mListener = builder.mListener;
        mFilter = builder.mFilter;
    }

    @SuppressWarnings("unchecked")
    private static <T> WatchEvent<T> cast(WatchEvent<?> event) {
        return (WatchEvent<T>) event;
    }

    @Override
    public void start() throws Exception {
        mExecutor = Executors.newSingleThreadExecutor();
        mWatcherTask = mExecutor.submit(this);
    }

    @Override
    public void stop() {
        mWatcherTask.cancel(true);
        mWatcherTask = null;
        mExecutor.shutdown();
        mExecutor = null;
    }

    @Override
    public void run() {
        WatchService watchService;
        try {
            watchService = FileSystems.getDefault().newWatchService();
        } catch (IOException ioe) {
            throw new RuntimeException("Exception while creating watch service.", ioe);
        }
        Map<WatchKey, Path> watchKeyToDirectory = new HashMap<>();

        for (Path dir : mWatched) {
            try {
                if (mPreExistingAsCreated) {
                    try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
                        for (Path path : stream) {
                            if (mFilter.accept(path)) {
                                mListener.onEvent(Event.ENTRY_CREATE, dir.resolve(path));
                            }
                        }
                    }
                }

                WatchKey key = dir.register(watchService, ENTRY_CREATE, ENTRY_MODIFY, ENTRY_DELETE);
                watchKeyToDirectory.put(key, dir);
            } catch (IOException ioe) {
                LOGGER.error("Not watching '{}'.", dir, ioe);
            }
        }

        while (true) {
            if (Thread.interrupted()) {
                LOGGER.info("Directory watcher thread interrupted.");
                break;
            }

            WatchKey key;
            try {
                key = watchService.take();
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                continue;
            }

            Path dir = watchKeyToDirectory.get(key);
            if (dir == null) {
                LOGGER.warn("Watch key not recognized.");
                continue;
            }

            for (WatchEvent<?> event : key.pollEvents()) {
                if (event.kind().equals(OVERFLOW)) {
                    break;
                }

                WatchEvent<Path> pathEvent = cast(event);
                WatchEvent.Kind<Path> kind = pathEvent.kind();

                Path path = dir.resolve(pathEvent.context());
                if (mFilter.accept(path) && EVENT_MAP.containsKey(kind)) {
                    mListener.onEvent(EVENT_MAP.get(kind), path);
                }
            }

            boolean valid = key.reset();
            if (!valid) {
                watchKeyToDirectory.remove(key);
                LOGGER.warn("'{}' is inaccessible. Stopping watch.", dir);
                if (watchKeyToDirectory.isEmpty()) {
                    break;
                }
            }
        }
    }

    public interface Listener {
        void onEvent(Event event, Path path);
    }

    public static class Builder {

        private static final Filter<Path> NO_FILTER = path -> true;

        private Set<Path> mWatched = new HashSet<>();
        private boolean mPreExistingAsCreated = false;
        private Filter<Path> mFilter = NO_FILTER;
        private Listener mListener;

        public Builder addDirectories(String dirPath) {
            return addDirectories(Paths.get(dirPath));
        }

        public Builder addDirectories(Path dirPath) {
            mWatched.add(dirPath);
            return this;
        }

        public Builder addDirectories(Path... dirPaths) {
            for (Path dirPath : dirPaths) {
                mWatched.add(dirPath);
            }
            return this;
        }

        public Builder addDirectories(Iterable<? extends Path> dirPaths) {
            for (Path dirPath : dirPaths) {
                mWatched.add(dirPath);
            }
            return this;
        }

        public Builder setPreExistingAsCreated(boolean value) {
            mPreExistingAsCreated = value;
            return this;
        }

        public Builder setFilter(Filter<Path> filter) {
            mFilter = filter;
            return this;
        }

        public DirectoryWatcher build(Listener listener) {
            mListener = listener;
            return new DirectoryWatcher(this);
        }
    }
}