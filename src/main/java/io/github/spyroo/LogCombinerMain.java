package io.github.spyroo;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.TextFieldListCell;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.io.*;
import java.util.concurrent.*;


public class LogCombinerMain extends Application{

    private ListView logsList = new ListView();

    private static String apiKey = "";
    private LogCombiner lc;

    private final ObservableList<String> links = FXCollections.observableArrayList();
    private final HBox changeLogsForm = new HBox();
    private final HBox newLogNameForm = new HBox();
    private final HBox newLogSubmitForm = new HBox();
    private final VBox vbox = new VBox();
    private static final TextField combinedLogResult = new TextField();
    private static final Logger logger = LogManager.getLogger(LogCombinerMain.class);


    public static void main(String[] args){
        launch(args);
    }

    /**
     * Check to see if there is a current API key
     * @param overrideNew If true, will always prompt for a new key
     */
    public void checkApiKey(boolean overrideNew){
        try {
            File apiKeyFile = new File("apikey.txt");
            if (!apiKeyFile.exists()) {
                boolean newFileCreatedSuccess = apiKeyFile.createNewFile();
                logger.debug("API key file did not exist, created new API key file");
            }
            FileReader fr = new FileReader(apiKeyFile);
            BufferedReader br = new BufferedReader(fr);
            logger.debug("Reading API key file...");
            apiKey = br.readLine();
            logger.debug("Read API key as " + apiKey);
            if (overrideNew || apiKey == null || apiKey.length() < 20 ) {
                apiKey = getApiKeyFromUser();
            }

            FileWriter fw = new FileWriter(apiKeyFile);
            BufferedWriter bw = new BufferedWriter(fw);
            bw.write(apiKey);
            bw.close();
            br.close();
        }catch(Exception e){
            logger.error(e);
        }
    }

    /**
     * Display an input box to the user to grab a new API key
     * @return The new API key
     */
    public String getApiKeyFromUser(){
        logger.debug("Getting new API key from user");
        return (String)JOptionPane.showInputDialog(
                null,
                "Enter your logs.tf api key from http://logs.tf/uploader",
                "Api Key",
                JOptionPane.PLAIN_MESSAGE,
                null,
                null,
                "key");
    }

    @Override
    public void start(final Stage primaryStage){

        lc = new LogCombiner("spyros log combiner", apiKey);

        logger.debug("Creating GUI");
        final HBox title = new HBox(3);
        final Label titleLabel = new Label("Combine logs.tf logs");
        titleLabel.setFont(new Font("Arial", 20));
        final Button apiButton = new Button("Change API key");
        apiButton.setOnAction(new EventHandler<ActionEvent>() {
            public void handle(ActionEvent actionEvent) {
                checkApiKey(true);
            }
        });
        title.getChildren().addAll(titleLabel, apiButton);

        primaryStage.setTitle("Combine Logs");
        logsList.setEditable(true);

        final TextField addLogLink = new TextField();
        addLogLink.setPromptText("Log Link");
        addLogLink.setMaxWidth(Double.MAX_VALUE);
        addLogLink.setOnAction(new EventHandler<ActionEvent>() {
            public void handle(ActionEvent actionEvent) {
                links.add(lc.getCleanLogsLink(addLogLink.getText()));
                addLogLink.clear();
            }
        });
        final Button addButton = new Button("Add");
        addButton.setOnAction(new EventHandler<ActionEvent>() {
            public void handle(ActionEvent actionEvent) {
                links.add(lc.getCleanLogsLink(addLogLink.getText()));
                addLogLink.clear();
            }
        });
        final Button removeButton = new Button("Remove");
        removeButton.setOnAction(new EventHandler<ActionEvent>() {
            public void handle(ActionEvent actionEvent) {
                links.remove(links.size() - 1);
            }
        });
        changeLogsForm.getChildren().addAll(addLogLink, addButton, removeButton);
        changeLogsForm.setSpacing(3);
        HBox.setHgrow(addLogLink, Priority.ALWAYS);

        logsList.setItems(links);
        logsList.setCellFactory(TextFieldListCell.forListView());

        final Label subTitle = new Label("Details");
        subTitle.setFont(new Font("Arial", 16));

        final TextField newLogName = new TextField();
        newLogName.setPromptText("New Log Name");
        final TextField newLogMap = new TextField();
        newLogMap.setPromptText("New Log Map");


        newLogNameForm.setSpacing(3);
        newLogNameForm.getChildren().addAll(newLogName, newLogMap);
        HBox.setHgrow(newLogName, Priority.ALWAYS);

        combinedLogResult.setPromptText("Result");
        combinedLogResult.setEditable(false);
        final Button copyButton = new Button("Copy");
        copyButton.setMaxWidth(Double.MAX_VALUE);
        copyButton.setOnAction(new EventHandler<ActionEvent>() {
            public void handle(ActionEvent actionEvent) {
                StringSelection stringSelection = new StringSelection(combinedLogResult.getText());
                Clipboard clpbrd = Toolkit.getDefaultToolkit().getSystemClipboard();
                clpbrd.setContents(stringSelection, null);
            }
        });

        newLogSubmitForm.setSpacing(3);
        newLogSubmitForm.getChildren().addAll(combinedLogResult, copyButton);
        HBox.setHgrow(combinedLogResult, Priority.ALWAYS);

        final ProgressBar progressBar = new ProgressBar(0);
        progressBar.setMaxWidth(Double.MAX_VALUE);

        final Button submitButton = new Button("Submit");
        submitButton.setMaxWidth(Double.MAX_VALUE);
        submitButton.setOnAction(new EventHandler<ActionEvent>() {
            public void handle(ActionEvent actionEvent) {
                ExecutorService pool = Executors.newFixedThreadPool(3);
                Runnable worker = new LogsWorker(filterText(newLogName.getText()), filterText(newLogMap.getText()), lc, links, progressBar);
                pool.submit(worker);
            }
        });


        vbox.setSpacing(5);
        vbox.setPadding(new Insets(10, 10, 10, 10));
        vbox.getChildren().addAll(title, logsList, changeLogsForm, subTitle, newLogNameForm, submitButton, progressBar, newLogSubmitForm);
        vbox.setFillWidth(true);

        primaryStage.setScene(new Scene(vbox));

        checkApiKey(false);
        lc.setLogsApiKey(apiKey);
        primaryStage.show();

        primaryStage.setOnCloseRequest(new EventHandler<WindowEvent>() {
            public void handle(WindowEvent e) {
                lc.delDir();
                Platform.exit();
                System.exit(0);
            }
        });
    }

    /**
     * Filter all the /'s from the input
     * @param unfiltered The unfiltered text
     * @return Filtered text with / replaced by -
     */
    private String filterText(String unfiltered){
        return unfiltered.replaceAll("/", "-");
    }

    /**
     * Update the response from a LogsWorker
     * @param response The LogsResponse reference to grab info from
     */
    public static void updateResponse(LogsResponse response){
        logger.debug("Updating response");
        if(response.getSuccess()) {
            combinedLogResult.setText("http://logs.tf" + response.getUrl());
        }else{
            combinedLogResult.setText(response.getError());
        }
    }

}
