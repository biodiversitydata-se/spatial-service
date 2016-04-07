/*
 * Copyright (C) 2016 Atlas of Living Australia
 * All Rights Reserved.
 *
 * The contents of this file are subject to the Mozilla Public
 * License Version 1.1 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of
 * the License at http://www.mozilla.org/MPL/
 *
 * Software distributed under the License is distributed on an "AS
 * IS" basis, WITHOUT WARRANTY OF ANY KIND, either express or
 * implied. See the License for the specific language governing
 * rights and limitations under the License.
 */

package au.org.ala.spatial.process

class Thumbnails extends SlaveProcess {

    final int THUMBNAIL_WIDTH = 200;
    final int THUMBNAIL_HEIGHT = 200;

    void start() {
        //only updates missing thumbnails
        List layers = getLayers()
        layers.each { layer ->
            String path = '/public/thumbnail/'
            task.message = 'checking thumbnail: ' + layer.name
            if (!hasThumbnail(layer.name.toString(), path)) {
                task.message = 'getting thumbnail: ' + layer.name
                one(layer, grailsApplication.config.data.dir + path)
            }
            addOutput('file', path + layer.name + '.jpg')
        }
    }

    void hasThumbnail(String layerName, String thumbnailDir) {
        slaveService.peekFile(thumbnailDir + layerName)[0].exists
    }

    void one(layer, thumbnailDir) {
        try {
            String geoserverUrl = layer.displaypath
            //trim from /wms or /gwc
            int wmsPos = geoserverUrl.indexOf("/wms")
            if (wmsPos >= 0) {
                geoserverUrl = geoserverUrl.substring(0, wmsPos)
            }
            int gwcPos = geoserverUrl.indexOf("/gwc")
            if (gwcPos >= 0) {
                geoserverUrl = geoserverUrl.substring(0, gwcPos)
            }

            String thumburl = geoserverUrl + "/wms/reflect?layers=ALA:" + layer.name +
                    "&width=" + THUMBNAIL_WIDTH + "&height=" + THUMBNAIL_HEIGHT

            URL url = new URL(thumburl)

            InputStream inputStream = new BufferedInputStream(url.openStream())
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[1024]
            int n
            while ((n = inputStream.read(buf)) > 0) {
                out.write(buf, 0, n)
            }
            out.close()
            inputStream.close()

            String filename = thumbnailDir + layer.name + '.jpg'
            File f = new File(filename)
            if (f.exists()) {
                f.delete()
            }

            f.getParentFile().mkdirs()

            FileOutputStream fos = new FileOutputStream(filename)
            fos.write(out.toByteArray())
            fos.close()
        } catch (IOException ex) {
            log.error("failed to create thumbnail for layer: " + layer + ", thumbnailDir: " + thumbnailDir, ex)
        }
    }
}
