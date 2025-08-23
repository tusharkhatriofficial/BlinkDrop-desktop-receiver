package com.blinkdrop;
import java.awt.*;
import java.io.IOException;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.stage.WindowEvent;

public class BlinkDropApp extends Application {

    private Receiver receiverService;
    private TrayIcon trayIcon;
    private boolean isRunning = false;

    public static void main(String[] args){
        Platform.setImplicitExit(false);
        launch(args);
    }

    @Override
    public void start(Stage primaryStage) throws Exception{
        // Hiding the main stage as we only want a tray icon
        setupControlWindow(primaryStage);
//        primaryStage.hide();
//        if(SystemTray.isSupported()){
//            setupTrayIcon();
//            startReceiverService();
//        }else{
//            System.err.println("System tray not supported!");
//            Platform.exit();
//        }
    }

    private void setupControlWindow(Stage primaryStage) {
        Label titleLabel = new Label("Welcome to BlinkDrop");
        titleLabel.setStyle("-fx-font-size: 22px; -fx-font-weight: bold; -fx-text-fill: #58a6ff;");

        Label subtitleLabel = new Label("Seamless file sharing. Directly from Android to MacOS.");
        subtitleLabel.setStyle("-fx-font-size: 14px; -fx-text-fill: #c9d1d9;");

        Button toggleButton = new Button("Start Receiving");
        toggleButton.setPrefWidth(160);
        toggleButton.setOnAction(event -> {
            if (!isRunning) {
                startReceiverService();
                subtitleLabel.setText("Mac is discoverable on wifi network...");
                toggleButton.setText("Stop Receiving");
                isRunning = true;
            } else {
                stopReceiverService();
                subtitleLabel.setText("Receiving stopped.");
                toggleButton.setText("Start Receiving");
                isRunning = false;
            }
        });

        VBox layout = new VBox(15, titleLabel, subtitleLabel, toggleButton);
        layout.setAlignment(Pos.CENTER);
        layout.setPadding(new Insets(40));
        layout.setStyle("-fx-background-color: #0d1117;");

        Scene scene = new Scene(layout, 400, 200);
        scene.getStylesheets().add(getClass().getResource("/styles.css").toExternalForm());

        primaryStage.setTitle("BlinkDrop");
        primaryStage.setScene(scene);
        primaryStage.setResizable(false);

        // Proper exit on window close
        primaryStage.setOnCloseRequest((WindowEvent e) -> {
            stopReceiverService();
            Platform.exit();
            System.exit(0);
        });

        primaryStage.show();
    }



    private void setupTrayIcon() throws IOException {
        // Load icon from resources
        SystemTray tray = SystemTray.getSystemTray();
//        SystemTray tray = System.getSystemTray();
        java.awt.Image image = Toolkit.getDefaultToolkit().getImage(getClass().getResource("/icon.png"));
        PopupMenu popup = new PopupMenu();
        MenuItem exitItem = new MenuItem("Exit");
        exitItem.addActionListener(e -> {
            stopReceiverService();
            Platform.exit();
            tray.remove(trayIcon);
            System.exit(0);
        });
        popup.add(exitItem);


        // Create the tray icon
        trayIcon = new TrayIcon(image, "BlinkDrop Receiver", popup);
        trayIcon.setImageAutoSize(true);
        trayIcon.setToolTip("BlinkDrop is running...");
        try {
            tray.add(trayIcon);
        } catch (AWTException e) {
            System.err.println("TrayIcon could not be added.");
        }

    }

    private void startReceiverService(){
        Receiver receiverService = new Receiver();
        Thread receiverThread = new  Thread(()->{
            try{
                receiverService.main(new String[0]);
            }catch (Exception e){
                e.printStackTrace();
            }
        });
        receiverThread.setDaemon(true);
        receiverThread.start();
    }

    private void stopReceiverService(){
        Receiver receiverService = new Receiver();
    }


}