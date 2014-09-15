/*
 * Copyright (c) 2014 Mario Guggenberger <mario.guggenberger@aau.at>
 *
 * This file is part of ITEC MediaPlayer.
 *
 * ITEC MediaPlayer is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * ITEC MediaPlayer is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with ITEC MediaPlayer.  If not, see <http://www.gnu.org/licenses/>.
 */

package at.aau.itec.android.mediaplayer.dash;

import android.os.AsyncTask;
import android.util.Log;
import android.util.Xml;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.concurrent.ExecutionException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import at.aau.itec.android.mediaplayer.UriSource;

/**
 * Created by maguggen on 27.08.2014.
 */
public class DashParser {

    private static final String TAG = DashParser.class.getSimpleName();

    private static Pattern PATTERN_TIME = Pattern.compile("PT((\\d+)H)?((\\d+)M)?((\\d+(\\.\\d+)?)S)");

    public MPD parse(final UriSource source) {
        // TODO clean up this mess
        try {
            // blocking async download and parsing of MPD XML file to avoid NetworkOnMainThreadException
            return new AsyncTask<Void, Void, MPD>() {
                @Override
                protected MPD doInBackground(Void... params) {
                    InputStream in = downloadMPD(source);
                    try {
                        return parse(in);
                    } catch (XmlPullParserException e) {
                        e.printStackTrace();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    return null;
                }
            }.execute().get();
        } catch (InterruptedException e) {
            e.printStackTrace();
        } catch (ExecutionException e) {
            e.printStackTrace();
        }

        return null;
    }

    private InputStream downloadMPD(UriSource source) {
        try {
            URL url = new URL(source.getUri().toString());
            HttpURLConnection urlConnection = (HttpURLConnection) url.openConnection();

            // TODO add headers
            //urlConnection.addRequestProperty();

            return new BufferedInputStream(urlConnection.getInputStream());
        } catch (MalformedURLException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }

        return null;
    }

    private MPD parse(InputStream in) throws XmlPullParserException, IOException {
        try {
            XmlPullParser parser = Xml.newPullParser();
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false);
            parser.setInput(in, null);

            String baseUrl = "";
            MPD mpd = new MPD();

            int type = 0;
            while((type = parser.nextTag()) >= 0) {
                if(type == XmlPullParser.START_TAG) {
                    String tagName = parser.getName();

                    if(tagName.equals("MPD")) {
                        mpd.mediaPresentationDurationUs = getAttributeValueTime(parser, "mediaPresentationDuration");
                        mpd.minBufferTimeUs = getAttributeValueTime(parser, "minBufferTime");
                    } else if(tagName.equals("BaseURL")) {
                        baseUrl = parser.nextText();
                        Log.d(TAG, "base url: " + baseUrl);
                    } else if(tagName.equals("AdaptationSet")) {
                        mpd.adaptationSets.add(readAdaptationSet(mpd, baseUrl, parser));
                    }
                } else if(type == XmlPullParser.END_TAG) {
                    String tagName = parser.getName();
                    if(tagName.equals("MPD")) {
                        break;
                    }
                }
            }

            Log.d(TAG, mpd.toString());

            return mpd;
        } finally {
            in.close();
        }
    }

    private AdaptationSet readAdaptationSet(MPD mpd, String baseUrl, XmlPullParser parser)
            throws XmlPullParserException, IOException {
        AdaptationSet adaptationSet = new AdaptationSet();

        adaptationSet.group = getAttributeValueInt(parser, "group");
        adaptationSet.mimeType = getAttributeValue(parser, "mimeType");

        int type = 0;
        while((type = parser.nextTag()) >= 0) {
            if(type == XmlPullParser.START_TAG) {
                String tagName = parser.getName();

                if(tagName.equals("Representation")) {
                    adaptationSet.representations.add(readRepresentation(mpd, adaptationSet, baseUrl, parser));
                }
            } else if(type == XmlPullParser.END_TAG) {
                String tagName = parser.getName();
                if(tagName.equals("AdaptationSet")) {
                    return adaptationSet;
                }
            }
        }

        throw new RuntimeException("invalid state");
    }

    private Representation readRepresentation(MPD mpd, AdaptationSet adaptationSet, String baseUrl, XmlPullParser parser)
            throws XmlPullParserException, IOException {
        Representation representation = new Representation();

        representation.id = getAttributeValue(parser, "id");
        representation.codec = getAttributeValue(parser, "codecs");
        representation.mimeType = getAttributeValue(parser, "mimeType", adaptationSet.mimeType);
        if(representation.mimeType.startsWith("video/")) {
            representation.width = getAttributeValueInt(parser, "width");
            representation.height = getAttributeValueInt(parser, "height");
        }
        representation.bandwidth = getAttributeValueInt(parser, "bandwidth");

        Log.d(TAG, representation.toString());

        int type = 0;
        while((type = parser.nextTag()) >= 0) {
            String tagName = parser.getName();

            if(type == XmlPullParser.START_TAG) {
                if (tagName.equals("Initialization")) {
                    representation.initSegment = new Segment(
                            baseUrl + getAttributeValue(parser, "sourceURL"),
                            getAttributeValue(parser, "range"));
                    Log.d(TAG, "Initialization: " + representation.initSegment.toString());
                } else if(tagName.equals("SegmentList")) {
                    representation.segmentDurationUs = getAttributeValueInt(parser, "duration") * 1000000L;
                } else if(tagName.equals("SegmentURL")) {
                    representation.segments.add(new Segment(
                            baseUrl + getAttributeValue(parser, "media"),
                            getAttributeValue(parser, "mediaRange")));
                } else if(tagName.equals("SegmentTemplate")) {
                    long timescale = getAttributeValueLong(parser, "timescale");
                    long duration = getAttributeValueLong(parser, "duration");
                    representation.segmentDurationUs = (duration / timescale) * 1000000L;
                    int startNumber = getAttributeValueInt(parser, "startNumber");
                    int numSegments = (int)Math.ceil((double)mpd.mediaPresentationDurationUs / representation.segmentDurationUs);

                    // init segments
                    representation.initSegment = new Segment(baseUrl + getAttributeValue(parser, "initialization"));

                    // media segments
                    String mediaUrl = getAttributeValue(parser, "media");
                    for(int i = startNumber; i < startNumber + numSegments; i++) {
                        representation.segments.add(new Segment(baseUrl + mediaUrl.replace("$Number$", i+"")));
                    }
                } else if(tagName.equals("BaseURL")) {
                    String newBaseUrl = parser.nextText();
                    Log.d(TAG, "new base url: " + newBaseUrl);
                }
            } else if(type == XmlPullParser.END_TAG) {
                if(tagName.equals("Representation")) {
                    return representation;
                }
            }
        }

        throw new RuntimeException("invalid state");
    }

    private void skip(XmlPullParser parser) throws XmlPullParserException, IOException {
        if (parser.getEventType() != XmlPullParser.START_TAG) {
            throw new IllegalStateException();
        }
        int depth = 1;
        while (depth != 0) {
            switch (parser.next()) {
                case XmlPullParser.END_TAG:
                    depth--;
                    break;
                case XmlPullParser.START_TAG:
                    depth++;
                    break;
            }
        }
    }

    /**
     * Parse a timestamp and return its duration in microseconds.
     * http://en.wikipedia.org/wiki/ISO_8601#Durations
     */
    private static long parseTime(String time) {
        Matcher matcher = PATTERN_TIME.matcher(time);

        if(matcher.matches()) {
            long hours = 0;
            long minutes = 0;
            double seconds = 0;

            String group = matcher.group(2);
            if (group != null) {
                hours = Long.parseLong(group);
            }
            group = matcher.group(4);
            if (group != null) {
                minutes = Long.parseLong(group);
            }
            group = matcher.group(6);
            if (group != null) {
                seconds = Double.parseDouble(group);
            }

            return (long) (seconds * 1000 * 1000)
                    + minutes * 60 * 1000 * 1000
                    + hours * 60 * 60 * 1000 * 1000;
        }

        return -1;
    }

    private static String getAttributeValue(XmlPullParser parser, String name, String def) {
        String value = parser.getAttributeValue(null, name);
        return value != null ? value : def;
    }

    private static String getAttributeValue(XmlPullParser parser, String name) {
        return getAttributeValue(parser, name, null);
    }

    private static int getAttributeValueInt(XmlPullParser parser, String name) {
        return Integer.parseInt(getAttributeValue(parser, name, "0"));
    }

    private static long getAttributeValueLong(XmlPullParser parser, String name) {
        return Long.parseLong(getAttributeValue(parser, name, "0"));
    }

    private static long getAttributeValueTime(XmlPullParser parser, String name) {
        return parseTime(getAttributeValue(parser, name));
    }
}