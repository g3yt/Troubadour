            // Step 1: Get the player response to obtain stream URLs
            Map<String, Object> playerResponse = getLiveStreamPlayerResponse(videoId);
            if (playerResponse == null || !(playerResponse instanceof Map)) {
                sendErrorResponse(out, 502, "Bad Gateway");
                return;
            }

            Map<String, Object> responseMap = (Map<String, Object>) playerResponse;

            // Check if this is a live stream
            boolean isLive = false;
            Object isLiveObj = responseMap.get("isLive");
            if (isLiveObj instanceof Boolean) {
                isLive = (Boolean) isLiveObj;
            }
            // Also check for liveBroadcastContent
            Object liveBroadcastContentObj = responseMap.get("liveBroadcastContent");
            if (liveBroadcastContentObj instanceof String) {
                String liveContent = (String) liveBroadcastContentObj;
                if ("live".equalsIgnoreCase(liveContent)) {
                    isLive = true;
                }
                // Check if stream has ended
                else if ("completed".equalsIgnoreCase(liveContent) ||
                         "ended".equalsIgnoreCase(liveContent)) {
                    // Stream has ended, handle as VOD
                    isLive = false;
                }
            }

            // Handle live streams differently
            if (isLive) {
                handleLiveStream(out, streamUrl, initStart, initLength, mediaSegments, rangeStart, rangeEnd, bandwidthMonitor, responseMap);
                return;
            }