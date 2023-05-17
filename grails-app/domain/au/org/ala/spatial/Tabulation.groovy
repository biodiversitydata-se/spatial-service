/**************************************************************************
 * Copyright (C) 2010 Atlas of Living Australia
 * All Rights Reserved.
 * <p>
 * The contents of this file are subject to the Mozilla Public
 * License Version 1.1 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of
 * the License at http://www.mozilla.org/MPL/
 * <p>
 * Software distributed under the License is distributed on an "AS
 * IS" basis, WITHOUT WARRANTY OF ANY KIND, either express or
 * implied. See the License for the specific language governing
 * rights and limitations under the License.
 ***************************************************************************/
package au.org.ala.spatial

/**
 * @author Adam
 */
import groovy.transform.CompileStatic
import org.locationtech.jts.geom.Geometry

@CompileStatic
class Tabulation {

    String fid1
    String pid1
    String fid2
    String pid2
    Double area
    Geometry geometry
    int occurrences
    int species
    Integer speciest1
    Integer speciest2

    static transients = ["name2", "name1"]
    String name2
    String name1

    static mapping = {
        version(false)

        geometry column: 'the_geom'
        fid1 index: 'tabulation_fid1_idx'
        fid2 index: 'tabulation_fid2_idx'
        pid1 index: 'tabulation_pid1_idx'
        pid2 index: 'tabulation_pid2_idx'
    }
}
