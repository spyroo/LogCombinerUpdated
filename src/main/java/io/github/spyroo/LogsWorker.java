package io.github.spyroo;

import javafx.collections.ObservableList;
import javafx.scene.control.ProgressBar;

import javax.swing.*;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class LogsWorker implements Runnable{

    private String newLogName;
    private String newLogMap;
    private LogCombiner lc;
    private ObservableList<String> links;
    private ProgressBar progressBar;

    public LogsWorker(String newLogName, String newLogMap, LogCombiner lc, ObservableList<String> links, ProgressBar progressBar){
        this.newLogMap = newLogMap;
        this.newLogName = newLogName;
        this.lc = lc;
        this.links = links;
        this.progressBar = progressBar;
    }

    public void run() {
        float incs = (1.0F / links.size()) / 3.0F;
        float total = 0F;
        String newName = newLogName;
        String newMap = newLogMap;
        LogsResponse resp = new LogsResponse();
        //clean > dl link > get file > combine
        Map<String, String> cleanToDL = new HashMap<String, String>();
        for (String s : links) {
            String clean = lc.getCleanLogsLink(s);
            cleanToDL.put(clean, lc.getLogsDownloadLink(clean));
            total += incs;
            System.out.println(total);
            progressBar.setProgress(total);
        }

        ArrayList<File> files = new ArrayList<File>();
        for (String s : cleanToDL.keySet()) {
            try {
                files.add(lc.getLogFile(cleanToDL.get(s), s));
                total += incs;
                System.out.println(total);
                progressBar.setProgress(total);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
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
            System.out.println(total);
            combinedLogs = lc.getCombinedFiles(fileArr, newName);
            total += incs;
            progressBar.setProgress(total);
            System.out.println(total);
            if(combinedLogs.length() > 5e+6){
                JOptionPane.showMessageDialog(null, "Combined file is too large for logs.tf", "Error", JOptionPane.CANCEL_OPTION);
                return;
            }
            resp = lc.sendLog(newName, newMap, combinedLogs);
            total += incs;
            progressBar.setProgress(total);
            System.out.println(total);
        } catch (Exception e) {
            //error, couldnt combine
            e.printStackTrace();
        }


        LogCombinerMain.updateResponse(resp);
    }

}
