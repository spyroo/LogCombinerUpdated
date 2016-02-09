package io.github.spyroo;

public class LogsResponse {

    private Boolean success;    //if it was a successful upload
    private String  url;        //the URL to the new log
    private String  error;      //if there was an error, the error that logs spit back at us

    public Boolean getSuccess() {
        return success;
    }

    public String getUrl() {
        return url;
    }

    public String getError() {
        return error;
    }
}
