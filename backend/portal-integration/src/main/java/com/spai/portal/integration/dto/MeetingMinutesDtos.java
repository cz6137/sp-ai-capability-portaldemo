package com.spai.portal.integration.dto;

import java.util.ArrayList;
import java.util.List;

public final class MeetingMinutesDtos {
    private MeetingMinutesDtos() {}

    public static class Capability {
        public boolean available;
        public boolean transcriptionAvailable;
        public boolean minutesAvailable;
        public String status;
        public String message;
        public int maxFileSizeMb;
        public List<String> acceptedExtensions = new ArrayList<String>();
        public List<String> externalProviders = new ArrayList<String>();
        public String processingLocation;
        public String retentionPolicy;
    }

    public static class JobView {
        public String id;
        public String status;
        public String stage;
        public int progress;
        public String fileName;
        public String mode;
        public String error;
        public String transcript;
        public MinutesResult result;
    }

    public static class MinutesResult {
        public String summary = "";
        public List<String> keypoints = new ArrayList<String>();
        public List<String> decisions = new ArrayList<String>();
        public List<String> actions = new ArrayList<String>();
    }
}
