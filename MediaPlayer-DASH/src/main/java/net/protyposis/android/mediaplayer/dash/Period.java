/*
 * Copyright (c) 2015 Mario Guggenberger <mg@protyposis.net>
 *
 * This file is part of MediaPlayer-Extended.
 *
 * MediaPlayer-Extended is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * MediaPlayer-Extended is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with MediaPlayer-Extended.  If not, see <http://www.gnu.org/licenses/>.
 */

package net.protyposis.android.mediaplayer.dash;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by Mario on 13.08.2015.
 */
public class Period {

    String id;
    long startUs;
    long durationUs;
    boolean bitstreamSwitching;
    List<AdaptationSet> adaptationSets;

    Period() {
        adaptationSets = new ArrayList<AdaptationSet>();
    }

    public List<AdaptationSet> getAdaptationSets() {
        return adaptationSets;
    }

    public AdaptationSet getFirstSetOfType(String mime) {
        for(AdaptationSet as : adaptationSets) {
            if(as.mimeType != null && as.mimeType.startsWith(mime)) {
                return as;
            } else {
                for(Representation r : as.representations) {
                    if(r.mimeType.startsWith(mime)) {
                        return as;
                    }
                }
            }
        }
        return null;
    }

    public AdaptationSet getFirstVideoSet() {
        return getFirstSetOfType("video/");
    }

    public AdaptationSet getFirstAudioSet() {
        return getFirstSetOfType("audio/");
    }
}
