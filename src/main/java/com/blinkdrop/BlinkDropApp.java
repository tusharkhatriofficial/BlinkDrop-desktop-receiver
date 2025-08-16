package com.blinkdrop;
import java.awt.*;
import java.io.IOException;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;

public class BlinkDropApp extends Application {

    private Receiver receiverService;
    private TrayIcon trayIcon;

    public static void main(String[] args){
        Platform.setImplicitExit(false);
        launch(args);
    }

    @Override
    public void start(Stage primaryStage) throws Exception{
        // Hiding the main stage as we only want a tray icon
        setupControlWindow(primaryStage);
//        primaryStage.hide();
        if(SystemTray.isSupported()){
            setupTrayIcon();
            startReceiverService();
        }else{
            System.err.println("System tray not supported!");
            Platform.exit();
        }
    }

    private void setupControlWindow(Stage primaryStage) {
        Label statusLabel = new Label("Service is running...");
        Button toggleButton = new Button("Stop Service");

        toggleButton.setOnAction(event -> {
            if (toggleButton.getText().equals("Start Service")) {
                startReceiverService();
                statusLabel.setText("Service is running...");
                toggleButton.setText("Stop Service");
            } else {
                stopReceiverService();
                statusLabel.setText("Service is stopped.");
                toggleButton.setText("Start Service");
            }
        });

        VBox layout = new VBox(10);
        layout.setPadding(new Insets(10.0, 0.0, 0.0, 0.0));
//        layout.setPadding(new Insets(10, 10, 10, 10));
        layout.getChildren().addAll(statusLabel, toggleButton);
                //.addAll(statusLabel, toggleButton);

        Scene scene = new Scene(layout, 250, 100);
        primaryStage.setTitle("BlinkDrop Control");
        primaryStage.setScene(scene);

        // Handle window closure: shut down the service
        primaryStage.setOnCloseRequest(event -> {
            stopReceiverService();
            Platform.exit();
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
        System.exit(0);
    }


}