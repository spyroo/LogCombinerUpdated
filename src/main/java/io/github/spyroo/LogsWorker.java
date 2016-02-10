package io.github.spyroo;

import javafx.collections.ObservableList;
import javafx.scene.control.ProgressBar;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.swing.*;
import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

public class LogsWorker implements Runnable{

    private String newLogName;
    private String newLogMap;
    private LogCombiner lc;
    private ObservableList<String> links;
    private ProgressBar progressBar;
    private static final Logger logger = LogManager.getLogger(LogsWorker.class);

    public LogsWorker(String newLogName, String newLogMap, LogCombiner lc, ObservableList<String> links, ProgressBar progressBar){
        this.newLogMap = newLogMap;
        this.newLogName = newLogName;
        this.lc = lc;
        this.links = links;
        this.progressBar = progressBar;
        logger.trace("Created new LogsWorker object");
    }

    public void run() {
        logger.trace("Starting worker process...");
        float incs = (1.0F / links.size()) / 3.0F;
        float total = 0F;
        String newName = newLogName;
        String newMap = newLogMap;
        LogsResponse resp = new LogsResponse();
        //clean > dl link > get file > combine
        Map<String, String> cleanToDL = new LinkedHashMap<String, String>();
        logger.trace("Creating download links from all clean links...");
        for (String s : links) {
            String clean = lc.getCleanLogsLink(s);
            cleanToDL.put(clean, lc.getLogsDownloadLink(clean));
            total += incs;
            progressBar.setProgress(total);
        }

        logger.trace("Getting all log files from URLs...");
        ArrayList<File> files = new ArrayList<File>();
        for (String s : cleanToDL.keySet()) {
            try {
                files.add(lc.getLogFile(cleanToDL.get(s), s));
                total += incs;
                progressBar.setProgress(total);
            } catch (Exception e) {
                logger.error(e);
            }
        }

        logger.trace("Combining all the logs now...");
        File combinedLogs;
        try {
            File[] fileArr = new File[files.size()];
            int index = 0;
            for (File f : files) {
                fileArr[index++] = f;
            }
            incs = (1.0F - total) / 3.0F;
            total += incs;
            progressBar.setProgress(total);
            combinedLogs = lc.getCombinedFiles(fileArr, newName);
            total += incs;
            progressBar.setProgress(total);
            if(combinedLogs.length() > 5e+6){
                JOptionPane.showMessageDialog(null, "Combined file is too large for logs.tf", "Error", JOptionPane.CANCEL_OPTION);
                return;
            }
            resp = lc.sendLog(newName, newMap, combinedLogs);
            total += incs;
            progressBar.setProgress(total);
        } catch (Exception e) {
            logger.error(e);
        }

        logger.trace("Done combining and uploading!");
        LogCombinerMain.updateResponse(resp);
    }

}
