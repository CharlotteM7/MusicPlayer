package app.musicplayer;

import app.musicplayer.controllers.ImportLibraryController;
import app.musicplayer.controllers.MainController;
import app.musicplayer.controllers.NowPlayingController;
import app.musicplayer.model.Album;
import app.musicplayer.model.Artist;
import app.musicplayer.model.Library;
import app.musicplayer.model.Song;
import app.musicplayer.model.serializable.Serializer;
import com.almasb.fxgl.logging.ConsoleOutput;
import com.almasb.fxgl.logging.Logger;
import com.almasb.fxgl.logging.LoggerLevel;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.scene.layout.BorderPane;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.nio.file.Files;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.LogManager;

import static app.musicplayer.Config.*;

// TODO: adding new songs after app closed
// TODO: adding new songs when app is running (add rescan button?)
// TODO: serialization version for future updates
// TODO: most played and recently played are not serialized back as instances of classes
// TODO: volume is not serialized
// TODO: light and dark themes
// TODO: on startup "Your Library" is not focused
public class MusicPlayerApp extends Application {

    private static final Logger log = Logger.get(MusicPlayerApp.class);

    private static MainController mainController;
    private static MediaPlayer mediaPlayer;
    private static List<Song> nowPlayingList;
    private static int nowPlayingIndex;
    private static Song nowPlaying;
    private static int timerCounter;
    private static int secondsPlayed;
    private static boolean isLoopActive = false;
    private static boolean isShuffleActive = false;
    private static boolean isMuted = false;
    private static Object draggedItem;
    private static ExecutorService executorService;

    private static Library library;

    private static Stage stage;

    public static class Launcher {
        public static void main(String[] args) {
            Application.launch(MusicPlayerApp.class);
        }
    }

    @Override
    public void start(Stage stage) throws Exception {
        try {
            // disable java.util.logging.Logger from jaudiotagger lib
            LogManager.getLogManager().reset();

            Logger.addOutput(new ConsoleOutput(), LoggerLevel.DEBUG);
            log.info("start(Stage)");

            executorService = Executors.newCachedThreadPool();

            timerCounter = 0;
            secondsPlayed = 0;

            MusicPlayerApp.stage = stage;
            MusicPlayerApp.stage.setMinWidth(800);
            MusicPlayerApp.stage.setMinHeight(600);
            MusicPlayerApp.stage.setTitle("Music Player");
            MusicPlayerApp.stage.getIcons().add(new Image(this.getClass().getResource(IMG + "Icon.png").toString()));
            MusicPlayerApp.stage.setOnCloseRequest(event -> {

                try {
                    if (library != null)
                        Serializer.writeToFile(library, LIBRARY_FILE);

                    executorService.shutdownNow();
                } catch (Exception e) {
                    log.warning("Error during exit", e);
                }
            });

            showSplashScreen(stage);

        } catch (Exception e) {
            log.fatal("Cannot start MusicPlayer", e);
            System.exit(0);
        }
    }

    private void showSplashScreen(Stage stage) throws Exception {
        FXMLLoader loader = new FXMLLoader(this.getClass().getResource(FXML + "SplashScreen.fxml"));
        Parent view = loader.load();

        ImportLibraryController controller = loader.getController();
        controller.setOwnerStage(stage);
        controller.setOnFinished(lib -> {
            library = lib;

            var task = new InitAppTask();
            executorService.submit(task);
        });

        if (Files.exists(LIBRARY_FILE)) {
            var lib = Serializer.readFromFile(LIBRARY_FILE);
            controller.loadFromLibrary(lib);
        }

        Scene scene = new Scene(view);
        stage.setScene(scene);
        stage.show();
    }

    private class InitAppTask extends Task<Void> {

        @Override
        protected Void call() throws Exception {
            nowPlayingList = new ArrayList<>();

            // TODO: check logic
            if (nowPlayingList.isEmpty()) {
                Artist artist = MusicPlayerApp.getLibrary().getArtists().get(0);

                for (Album album : artist.albums()) {
                    nowPlayingList.addAll(album.getSongs());
                }

                nowPlayingList.sort(Comparator.comparing(Song::getAlbum).thenComparing(song -> song));
            }

            nowPlaying = nowPlayingList.get(0);
            nowPlayingIndex = 0;
            nowPlaying.setPlaying(true);

            timerCounter = 0;
            secondsPlayed = 0;
            Media media = new Media(nowPlaying.getFile().toUri().toString());
            mediaPlayer = new MediaPlayer(media);
            mediaPlayer.setVolume(0.5);
            mediaPlayer.setOnEndOfMedia(new SongSkipper());

            return null;
        }

        @Override
        protected void succeeded() {
            showMain();
        }
    }

    /**
     * Initializes the main layout.
     */
    private void showMain() {
        try {
            // Load main layout from fxml file.
            FXMLLoader loader = new FXMLLoader(this.getClass().getResource(FXML + "Main.fxml"));
            BorderPane view = loader.load();

            // Shows the scene containing the layout.
            double width = stage.getScene().getWidth();
            double height = stage.getScene().getHeight();

            view.setPrefWidth(width);
            view.setPrefHeight(height);

            Scene scene = new Scene(view);
            stage.setScene(scene);

            // Gives the controller access to the music player main application.
            mainController = loader.getController();
            mediaPlayer.volumeProperty().bind(mainController.getVolumeSlider().valueProperty().divide(200));

        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    private static class SongSkipper implements Runnable {
        @Override
        public void run() {
            skip();
        }
    }

    private static class TimeUpdater extends TimerTask {
        private int length = (int) getNowPlaying().getLengthInSeconds() * 4;

        @Override
        public void run() {
            Platform.runLater(() -> {
                if (timerCounter < length) {
                    if (++timerCounter % 4 == 0) {
                        mainController.updateTimeLabels();
                        secondsPlayed++;
                    }


                    // TODO:
//                    if (!mainController.isTimeSliderPressed()) {
//                        mainController.updateTimeSlider();
//                    }
                }
            });
        }
    }

    /**
     * Plays selected song.
     */
    public static void play() {
        if (mediaPlayer != null && !isPlaying()) {
            mediaPlayer.play();

            // TODO:
            //timer.scheduleAtFixedRate(new TimeUpdater(), 0, 250);
            mainController.updatePlayPauseIcon(true);
        }
    }

    /**
     * Checks if a song is playing.
     */
    public static boolean isPlaying() {
        return mediaPlayer != null && MediaPlayer.Status.PLAYING.equals(mediaPlayer.getStatus());
    }

    /**
     * Pauses selected song.
     */
    public static void pause() {
        if (isPlaying()) {
            mediaPlayer.pause();
            // TODO:
            //timer.cancel();
            mainController.updatePlayPauseIcon(false);
        }
    }

    public static void seek(int seconds) {
        if (mediaPlayer != null) {
            mediaPlayer.seek(new Duration(seconds * 1000));
            timerCounter = seconds * 4;
            mainController.updateTimeLabels();
        }
    }

    /**
     * Skips song.
     */
    public static void skip() {
        if (nowPlayingIndex < nowPlayingList.size() - 1) {
            boolean isPlaying = isPlaying();
            mainController.updatePlayPauseIcon(isPlaying);
            setNowPlaying(nowPlayingList.get(nowPlayingIndex + 1));
            if (isPlaying) {
                play();
            }
        } else if (isLoopActive) {
            boolean isPlaying = isPlaying();
            mainController.updatePlayPauseIcon(isPlaying);
            nowPlayingIndex = 0;
            setNowPlaying(nowPlayingList.get(nowPlayingIndex));
            if (isPlaying) {
                play();
            }
        } else {
            mainController.updatePlayPauseIcon(false);
            nowPlayingIndex = 0;
            setNowPlaying(nowPlayingList.get(nowPlayingIndex));
        }
    }

    public static void back() {
        if (timerCounter > 20 || nowPlayingIndex == 0) {
            mainController.initializeTimeSlider();
            seek(0);
        } else {
            boolean isPlaying = isPlaying();
            setNowPlaying(nowPlayingList.get(nowPlayingIndex - 1));
            if (isPlaying) {
                play();
            }
        }
    }

    public static void mute(boolean isMuted) {
        MusicPlayerApp.isMuted = !isMuted;
        if (mediaPlayer != null) {
            mediaPlayer.setMute(!isMuted);
        }
    }

    public static void toggleLoop() {
        isLoopActive = !isLoopActive;
    }

    public static boolean isLoopActive() {
        return isLoopActive;
    }

    public static void toggleShuffle() {
        isShuffleActive = !isShuffleActive;

        if (isShuffleActive) {
            Collections.shuffle(nowPlayingList);
        } else {
            Collections.sort(nowPlayingList,
                    Comparator
                            .comparing(Song::getAlbum)
                            .thenComparing(song -> song)
            );
        }

        nowPlayingIndex = nowPlayingList.indexOf(nowPlaying);

        if (mainController.getSubViewController() instanceof NowPlayingController) {
            mainController.loadView("nowPlaying");
        }
    }

    public static boolean isShuffleActive() {
        return isShuffleActive;
    }

    public static Stage getStage() {
        return stage;
    }

    /**
     * Gets main controller object.
     * @return MainController
     */
    public static MainController getMainController() {
        return mainController;
    }

    /**
     * Gets currently playing song list.
     * @return arraylist of now playing songs
     */
    public static ArrayList<Song> getNowPlayingList() {
        return nowPlayingList == null ? new ArrayList<>() : new ArrayList<>(nowPlayingList);
    }

    public static void addSongToNowPlayingList(Song song) {
        if (!nowPlayingList.contains(song)) {
            nowPlayingList.add(song);
        }
    }

    public static void setNowPlayingList(List<Song> list) {
        nowPlayingList = new ArrayList<>(list);
    }

    public static void setNowPlaying(Song song) {
        if (nowPlayingList.contains(song)) {

            updatePlayCount();
            nowPlayingIndex = nowPlayingList.indexOf(song);
            if (nowPlaying != null) {
                nowPlaying.setPlaying(false);
            }
            nowPlaying = song;
            nowPlaying.setPlaying(true);
            if (mediaPlayer != null) {
                mediaPlayer.stop();
            }
            // TODO:
//            if (timer != null) {
//                timer.cancel();
//            }
//            timer = new Timer();
            timerCounter = 0;
            secondsPlayed = 0;
            Media media = new Media(song.getFile().toUri().toString());
            mediaPlayer = new MediaPlayer(media);
            mediaPlayer.volumeProperty().bind(mainController.getVolumeSlider().valueProperty().divide(200));
            mediaPlayer.setOnEndOfMedia(new SongSkipper());
            mediaPlayer.setMute(isMuted);
            mainController.updateNowPlayingButton();
            mainController.initializeTimeSlider();
            mainController.initializeTimeLabels();
        }
    }

    private static void updatePlayCount() {
        if (nowPlaying != null) {
            int length = (int) nowPlaying.getLengthInSeconds();
            if ((100 * secondsPlayed / length) > 50) {
                songPlayed(nowPlaying);
            }
        }
    }

    private static void songPlayed(Song song) {
        song.playCountProperty().set(song.playCountProperty().get() + 1);
        song.setPlayDate(LocalDateTime.now());
    }

    public static Song getNowPlaying() {
        return nowPlaying;
    }

    public static String getTimePassed() {
        int secondsPassed = timerCounter / 4;
        int minutes = secondsPassed / 60;
        int seconds = secondsPassed % 60;
        return Integer.toString(minutes) + ":" + (seconds < 10 ? "0" + seconds : Integer.toString(seconds));
    }

    public static String getTimeRemaining() {
        long secondsPassed = timerCounter / 4;
        long totalSeconds = getNowPlaying().getLengthInSeconds();
        long secondsRemaining = totalSeconds - secondsPassed;
        long minutes = secondsRemaining / 60;
        long seconds = secondsRemaining % 60;
        return Long.toString(minutes) + ":" + (seconds < 10 ? "0" + seconds : Long.toString(seconds));
    }

    public static void setDraggedItem(Object item) {
        draggedItem = item;
    }

    public static Object getDraggedItem() {
        return draggedItem;
    }

    public static Library getLibrary() {
        return library;
    }

    public static ExecutorService getExecutorService() {
        return executorService;
    }
}
