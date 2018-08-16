/*
 * Copyright 2015 Thomas Hoffmann
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package de.j4velin.gastzugang;

import android.text.Html;
import android.util.Xml;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

class Parser {

    static class Entry {
    }

    static class LoginEntry extends Entry {
        final String sid;
        final String challenge;
        final int blockTime;

        LoginEntry(final String sid, final String challenge, final int block) {
            this.sid = sid;
            this.challenge = challenge;
            this.blockTime = block;
        }

        @Override
        public String toString() {
            return sid + " " + challenge + " " + blockTime;
        }
    }

    List<Entry> parse(final InputStream in) throws XmlPullParserException, IOException {
        XmlPullParser parser = Xml.newPullParser();
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false);
        parser.setInput(in, null);
        return read(parser);
    }

    private List<Entry> read(final XmlPullParser parser) throws XmlPullParserException,
            IOException {
        List<Entry> entries = new ArrayList<Entry>();
        while (parser.next() != XmlPullParser.END_TAG && entries.isEmpty()) {
            if (parser.getEventType() != XmlPullParser.START_TAG) {
                continue;
            }
            String name = parser.getName();
            if (BuildConfig.DEBUG) Logger.log("reading " + name);
            // Starts by looking for the entry tag
            if (name.equals("SessionInfo")) {
                entries.add(readSessionInfo(parser));
            } else {
                skip(parser);
            }
        }
        return entries;
    }

    private LoginEntry readSessionInfo(final XmlPullParser parser) throws XmlPullParserException,
            IOException {
        parser.require(XmlPullParser.START_TAG, null, "SessionInfo");
        String sid = null, challenged = null, block = "-1";
        while (parser.next() != XmlPullParser.END_TAG && (sid == null || challenged == null)) {
            if (parser.getEventType() != XmlPullParser.START_TAG) {
                continue;
            }
            String name = parser.getName();
            if (BuildConfig.DEBUG) Logger.log("reading " + name);
            switch (name) {
                case "SID":
                    sid = readTag(parser, name);
                    break;
                case "Challenge":
                    challenged = readTag(parser, name);
                    break;
                case "BlockTime":
                    block = readTag(parser, name);
                    break;
                default:
                    skip(parser);
                    break;
            }
        }
        if (BuildConfig.DEBUG)
            Logger.log("returning logininfo " + sid + " / " + challenged + " / " + block);
        return new LoginEntry(sid, challenged, Integer.valueOf(block));
    }

    private String readTag(final XmlPullParser parser, final String tag) throws IOException,
            XmlPullParserException {
        parser.require(XmlPullParser.START_TAG, null, tag);
        String result = "";
        if (parser.next() == XmlPullParser.TEXT) {
            result = parser.getText();
            parser.nextTag();
        }
        parser.require(XmlPullParser.END_TAG, null, tag);
        if (BuildConfig.DEBUG) Logger.log(
                "tag read: original: " + result + " fromHtml: " + Html.fromHtml(result).toString());
        return Html.fromHtml(result).toString();
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

}
