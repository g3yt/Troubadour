/**
     * Extracts all media segments from the selected format and streaming data.
     * Supports multiple index formats: JSON segments, SIDX boxes, URL templates.
     * Returns a list of MediaSegment objects sorted by their start offset.
     */
    private List<MediaSegment> getMediaSegments(Map<String, Object> format, Map<String, Object> streamingData,
                                                BandwidthMonitor bandwidthMonitor) {
        List<MediaSegment> segments = new ArrayList<>();

        // Try multiple index sources in order of preference
        // 1. Try adaptive format's segments directly (for non-SABR formats)
        segments = extractSegmentsFromFormat(format);
        if (!segments.isEmpty()) {
            return segments;
        }

        // 2. Try indexRange in format (SABR style)
        Object indexRangeObj = format.get("indexRange");
        if (indexRangeObj instanceof Map) {
            List<MediaSegment> indexSegments = parseIndexRange(format, bandwidthMonitor);
            if (!indexSegments.isEmpty()) {
                return indexSegments;
            }
        }

        // 3. Try to find segments in streamingData
        segments = extractSegmentsFromStreamingData(streamingData, bandwidthMonitor);
        if (!segments.isEmpty()) {
            return segments;
        }

        // 4. Try to parse index from format's indexRange field
        Object indexRangeObj = format.get("indexRange");
        if (indexRangeObj instanceof Map) {
            List<MediaSegment> indexSegments = parseIndexRange(format, bandwidthMonitor);
            if (!indexSegments.isEmpty()) {
                return indexSegments;
            }
        }

        // 5. Fallback: treat format itself as a single segment
        Object startObj = format.get("start");
        Object lengthObj = format.get("length");
        if (startObj instanceof Number && lengthObj instanceof Number) {
            long start = ((Number) startObj).longValue();
            long length = ((Number) lengthObj).longValue();
            if (length > 0) {
                MediaSegment segment = new MediaSegment(start, length);
                segments.add(segment);
                return segments;
            }
        }

        // 6. Try to find segments in streamingData using known keys
        segments = extractSegmentsFromStreamingData(streamingData, bandwidthMonitor);
        if (!segments.isEmpty()) {
            return segments;
        }

        return segments;
    }

    /**
     * Extracts segments directly from a format object.
     * Handles formats that contain segments array directly.
     */
    private List<MediaSegment> extractSegmentsFromFormat(Map<String, Object> format) {
        List<MediaSegment> segments = new ArrayList<>();

        // Check for segments array directly in format
        Object segmentsObj = format.get("segments");
        if (segmentsObj instanceof List) {
            List<?> segmentsList = (List<?>) segmentsObj;
            for (Object obj : segmentsList) {
                if (obj instanceof Map) {
                    Map<String, Object> segMap = (Map<String, Object>) obj;
                    long start = -1;
                    long length = -1;

                    Object startObj = segMap.get("start");
                    if (startObj instanceof Number) start = ((Number) startObj).longValue();
                    else if (segMap.containsKey("offset")) {
                        Object val = segMap.get("offset");
                        if (val instanceof Number) start = ((Number) val).longValue();
                    } else if (segMap.containsKey("position")) {
                        Object val = segMap.get("position");
                        if (val instanceof Number) start = ((Number) val).longValue();
                    } else if (segMap.containsKey("begin")) {
                        Object val = segMap.get("begin");
                        if (val instanceof Number) start = ((Number) val).longValue();
                    }

                    Object lengthObj = segMap.get("length");
                    if (lengthObj instanceof Number) length = ((Number) lengthObj).longValue();
                    else if (segMap.containsKey("size")) {
                        Object val = segMap.get("size");
                        if (val instanceof Number) length = ((Number) val).longValue();
                    } else if (segMap.containsKey("duration")) {
                        Object val = segMap.get("duration");
                        if (val instanceof Number) length = ((Number) val).longValue();
                    }

                    if (start >= 0 && length > 0) {
                        segments.add(new MediaSegment(start, length));
                    }
                }
            }
        }

        // Also check for single segment properties directly on format
        if (segments.isEmpty()) {
            Object startObj = format.get("start");
            Object lengthObj = format.get("length");
            if (startObj instanceof Number && lengthObj instanceof Number) {
                long start = ((Number) startObj).longValue();
                long length = ((Number) lengthObj).longValue();
                if (length > 0) {
                    segments.add(new MediaSegment(start, length));
                }
            }
        }

        return segments;
    }

    /**
     * Parses indexRange from format to extract media segments.
     * Handles both direct segment info and index segment references.
     */
    private List<MediaSegment> parseIndexRange(Map<String, Object> format, BandwidthMonitor bandwidthMonitor) {
        List<MediaSegment> segments = new ArrayList<>();

        Object indexRangeObj = format.get("indexRange");
        if (!(indexRangeObj instanceof Map)) {
            return segments;
        }

        Map<String, Object> indexRangeMap = (Map<String, Object>) indexRangeObj;
        Object startObj = indexRangeMap.get("start");
        Object lengthObj = indexRangeMap.get("length");
        Object urlObj = format.get("url");

        if (!(startObj instanceof Number) || !(lengthObj instanceof Number) || !(urlObj instanceof String)) {
            return segments;
        }

        long indexStart = ((Number) startObj).longValue();
        long indexLength = ((Number) lengthObj).longValue();
        String indexUrl = (String) urlObj;

        // Fetch the index segment
        byte[] indexData = fetchStreamSegment((String) format.get("url"), indexStart, indexLength);
        if (indexData == null) {
            return segments;
        }

        // Record fetch size for bandwidth monitoring
        if (bandwidthMonitor != null) {
            bandwidthMonitor.recordFetch(indexData.length);
        }

        // Parse the index segment to extract media segments
        List<MediaSegment> indexSegments = parseIndexSegment(indexData);
        if (!indexSegments.isEmpty()) {
            return indexSegments;
        }

        // Fallback: treat index segment itself as a media segment
        segments.add(new MediaSegment(indexStart, indexLength));
        return segments;
    }

    /**
     * Extracts media segments from streamingData.
     * Looks for common YouTube segment locations in the response.
     */
    private List<MediaSegment> extractSegmentsFromStreamingData(Map<String, Object> streamingData, BandwidthMonitor bandwidthMonitor) {
        List<MediaSegment> segments = new ArrayList<>();
        if (streamingData == null) return segments;

        // Check adaptiveFormats
        Object adaptiveFormatsObj = streamingData.get("adaptiveFormats");
        if (adaptiveFormatsObj instanceof List) {
            List<?> formats = (List<?>) adaptiveFormatsObj;
            for (Object fmtObj : formats) {
                if (fmtObj instanceof Map) {
                    Map<String, Object> fmt = (Map<String, Object>) fmtObj;
                    List<MediaSegment> segs = extractSegmentsFromFormat(fmt);
                    if (!segs.isEmpty()) {
                        segments.addAll(segs);
                    }
                }
            }
        }

        // Check formats array
        if (segments.isEmpty()) {
            Object formatsObj = streamingData.get("formats");
            if (formatsObj instanceof List) {
                List<?> formats = (List<?>) formatsObj;
                for (Object fmtObj : formats) {
                    if (fmtObj instanceof Map) {
                        Map<String, Object> fmt = (Map<String, Object>) fmtObj;
                        List<MediaSegment> segs = extractSegmentsFromFormat(fmt);
                        if (!segs.isEmpty()) {
                            segments.addAll(segs);
                        }
                    }
                }
            }
        }

        return segments;
    }

    /**
     * Parses an index segment to extract media segment information.
     * Tries multiple formats: JSON, binary SIDX box, URL template.
     *
     * @param indexData The raw index segment data
     * @return List of MediaSegment objects, or empty list if parsing fails
     */
    private List<MediaSegment> parseIndexSegment(byte[] indexData) {
        if (indexData == null || indexData.length == 0) {
            return Collections.emptyList();
        }

        // Try JSON first
        List<MediaSegment> jsonSegments = parseIndexAsJson(indexData);
        if (!jsonSegments.isEmpty()) return jsonSegments;

        // Try binary sidx box
        List<MediaSegment> sidxSegments = parseIndexAsSidx(indexData);
        if (!sidxSegments.isEmpty()) return sidxSegments;

        // Try URL template pattern (common in SABR/DASH)
        List<MediaSegment> templateSegments = parseIndexAsUrlTemplate(indexData);
        if (!templateSegments.isEmpty()) return templateSegments;

        // Fallback: treat as single segment
        return Collections.emptyList();
    }

    /**
     * Tries to parse index data as JSON array of segments.
     * Supports various field name combinations:
     * - start/length
     * - offset/size
     * - position/length
     * - begin/duration
     */
    private List<MediaSegment> parseIndexAsJson(byte[] indexData) {
        if (indexData == null || indexData.length == 0) return Collections.emptyList();

        try {
            String jsonStr = new String(indexData, StandardCharsets.UTF_8);
            Object jsonObj = JsonUtils.parseAny(jsonStr);

            if (!(jsonObj instanceof List)) return Collections.emptyList();

            List<?> jsonList = (List<?>) jsonObj;
            List<MediaSegment> segments = new ArrayList<>();

            for (Object obj : jsonList) {
                if (!(obj instanceof Map)) continue;

                Map<String, Object> segmentMap = (Map<String, Object>) obj;
                long start = -1;
                long length = -1;

                // Try various field name combinations for start position
                if (segmentMap.containsKey("start")) {
                    Object val = segmentMap.get("start");
                    if (val instanceof Number) start = ((Number) val).longValue();
                } else if (segmentMap.containsKey("offset")) {
                    Object val = segmentMap.get("offset");
                    if (val instanceof Number) start = ((Number) val).longValue();
                } else if (segmentMap.containsKey("position")) {
                    Object val = segmentMap.get("position");
                    if (val instanceof Number) start = ((Number) val).longValue();
                } else if (segmentMap.containsKey("begin")) {
                    Object val = segmentMap.get("begin");
                    if (val instanceof Number) start = ((Number) val).longValue();
                }

                // Try various field name combinations for length/size
                long length = -1;
                if (segmentMap.containsKey("length")) {
                    Object val = segmentMap.get("length");
                    if (val instanceof Number) length = ((Number) val).longValue();
                } else if (segmentMap.containsKey("size")) {
                    Object val = segmentMap.get("size");
                    if (val instanceof Number) length = ((Number) val).longValue();
                } else if (segmentMap.containsKey("duration")) {
                    Object val = segmentMap.get("duration");
                    if (val instanceof Number) length = ((Number) val).longValue();
                }

                if (start >= 0 && length > 0) {
                    segments.add(new MediaSegment(start, length));
                }
            }

            return segments.isEmpty() ? Collections.emptyList() : segments;
        } catch (Exception e) {
            Log.d(TAG, "JSON index parsing failed: " + e.getMessage());
        }
        return Collections.emptyList();
    }

    /**
     * Tries to parse index data as URL template (common in SABR/DASH).
     * Looks for patterns like: "segment_{number}.m4s" or "segment_{time}.m4s"
     * or template strings with $Number$, $Time$ placeholders.
     */
    private List<MediaSegment> parseIndexAsUrlTemplate(byte[] indexData, String baseUrl) {
        List<MediaSegment> segments = new ArrayList<>();
        try {
            String text = new String(indexData, StandardCharsets.UTF_8).trim();

            // Check if it looks like a template (contains $Number$, $Time$, $RepresentationID$, etc.)
            if (text.contains("$Number$") || text.contains("$Time$") ||
                text.contains("$RepresentationID$") || text.contains("$Bandwidth$")) {

                // Try to extract segment count and duration from surrounding context
                // For now, return empty to fall back - full implementation would need
                // to parse MPD/manifest or use additional metadata
                return Collections.emptyList();
            }

            // Check if it's a simple list of URLs (one per line)
            if (text.contains("\n") && (text.contains("http://") || text.contains("https://"))) {
                String[] lines = text.split("\n");
                long start = 0;
                for (String line : lines) {
                    line = line.trim();
                    if (line.startsWith("http://") || line.startsWith("https://")) {
                        // For URL list, we don't know byte ranges - use sequential placeholder
                        segments.add(new MediaSegment(0, 0)); // Placeholder
                    }
                }
                if (!segments.isEmpty()) return segments;
            }
        } catch (Exception e) {
            Log.d(TAG, "URL template parsing failed: " + e.getMessage());
        }
        return Collections.emptyList();
    }

    /**
     * Tries to parse index data as binary sidx box (Segment Index Box from ISO BMFF / ISO/IEC 14496-12).
     *
     * @param indexData The raw index segment data
     * @return List of MediaSegment objects, or empty list if parsing fails
     */
    private List<MediaSegment> parseIndexAsSidx(byte[] indexData) {
        if (indexData == null || indexData.length < 8) return Collections.emptyList();

        try {
            DataInputStream dis = new DataInputStream(new ByteArrayInputStream(indexData));

            // Parse full box header
            int version = dis.readUnsignedByte();
            int flags = dis.readUnsignedShort() << 8 | dis.readUnsignedByte(); // Actually 24 bits
            long boxSize = dis.readUnsignedInt(); // 32-bit size

            // Check if this is actually an sidx box (type 'sidx')
            int type = dis.readInt();
            if (type != 0x73696478) { // 'sidx' in ASCII
                return Collections.emptyList(); // Not an sidx box
            }

            // Parse sidx-specific fields
            int referenceId = dis.readUnsignedInt();
            short versionField = dis.readUnsignedShort();
            short reserved = dis.readUnsignedShort();
            long earliestPresentationTime = dis.readUnsignedInt();
            long firstOffset = dis.readUnsignedInt();
            short referenceCount = dis.readUnsignedShort();

            // Skip reserved bytes
            if (version == 0) {
                dis.skipBytes(2); // reserved
            }

            List<MediaSegment> segments = new ArrayList<>();
            long currentOffset = firstOffset;

            // Parse each reference
            for (int i = 0; i < referenceCount; i++) {
                int referenceInfo = dis.readUnsignedInt();
                boolean referenceType = (referenceInfo & 0x80000000) != 0;
                long referencedSize = referenceInfo & 0x7FFFFFFF;

                int duration = dis.readUnsignedInt();
                int startsWithSync = (dis.readUnsignedInt() & 0x80000000) != 0 ? 1 : 0;
                long sapDelta = dis.readUnsignedInt() & 0x7FFFFFFF;

                if (!referenceType) { // Only media segments, not references to other index boxes
                    long start = currentOffset;
                    long length = referencedSize;
                    if (length > 0) {
                        segments.add(new MediaSegment(start, length));
                    }
                    currentOffset += referencedSize;
                } else {
                    // Reference to another index box - would need recursive parsing
                    currentOffset += dis.readUnsignedInt(); // referenced_size
                    dis.skipBytes(8 + 4); // subsegment_duration + starts_with_sap + sap_delta_time
                }
            }

            return segments.isEmpty() ? Collections.emptyList() : segments;
        } catch (Exception e) {
            Log.d(TAG, "SIDX index parsing failed: " + e.getMessage());
        }
        return Collections.emptyList();
    }