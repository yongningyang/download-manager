package com.novoda.downloadmanager.demo.extended.extra_data;

public class Download {
    private final String title;
    private final String extraData;

    public Download(String title, String extraData) {
        this.title = title;
        this.extraData = extraData;
    }

    public String getTitle() {
        return title;
    }

    public String getExtraData() {
        return extraData;
    }
}
