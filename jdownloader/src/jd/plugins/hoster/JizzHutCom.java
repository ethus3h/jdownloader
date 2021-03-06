//    jDownloader - Downloadmanager
//    Copyright (C) 2009  JD-Team support@jdownloader.org
//
//    This program is free software: you can redistribute it and/or modify
//    it under the terms of the GNU General Public License as published by
//    the Free Software Foundation, either version 3 of the License, or
//    (at your option) any later version.
//
//    This program is distributed in the hope that it will be useful,
//    but WITHOUT ANY WARRANTY; without even the implied warranty of
//    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
//    GNU General Public License for more details.
//
//    You should have received a copy of the GNU General Public License
//    along with this program.  If not, see <http://www.gnu.org/licenses/>.

package jd.plugins.hoster;

import java.io.IOException;

import jd.PluginWrapper;
import jd.http.Browser;
import jd.http.URLConnectionAdapter;
import jd.nutils.encoding.Encoding;
import jd.plugins.DownloadLink;
import jd.plugins.DownloadLink.AvailableStatus;
import jd.plugins.HostPlugin;
import jd.plugins.LinkStatus;
import jd.plugins.PluginException;
import jd.plugins.PluginForHost;

@HostPlugin(revision = "$Revision: 35931 $", interfaceVersion = 2, names = { "jizzhut.com" }, urls = { "https?://(www\\.)?jizzhut\\.com/videos/.*?\\.html" })
public class JizzHutCom extends PluginForHost {

    private String dllink = null;

    /* DEV NOTES */
    /* Porn_plugin */

    public JizzHutCom(PluginWrapper wrapper) {
        super(wrapper);
    }

    @Override
    public String getAGBLink() {
        return "http://www.jizzhut.com/help.php";
    }

    @Override
    public int getMaxSimultanFreeDownloadNum() {
        return -1;
    }

    @Override
    public void handleFree(DownloadLink downloadLink) throws Exception {
        requestFileInformation(downloadLink);
        dl = jd.plugins.BrowserAdapter.openDownload(br, downloadLink, dllink, true, 1);
        dl.startDownload();
    }

    @SuppressWarnings("deprecation")
    @Override
    public AvailableStatus requestFileInformation(final DownloadLink downloadLink) throws IOException, PluginException {
        this.setBrowserExclusive();
        br.getPage(downloadLink.getDownloadURL());
        // Link offline
        if (br.containsHTML("Datei nicht gefunden") || br.containsHTML("<p>This video does not exist or has been removed.</p>")) {
            throw new PluginException(LinkStatus.ERROR_FILE_NOT_FOUND);
        }
        // Link offline without any errormessage
        if (!br.containsHTML("(\\'|\")https?://(www\\.)?jizzhut\\.com/videos/embed/")) {
            throw new PluginException(LinkStatus.ERROR_FILE_NOT_FOUND);
        }
        String filename = br.getRegex("<title>(.*?)</title>").getMatch(0);
        if (filename == null) {
            filename = br.getRegex("DESCRIPTION\" content=\"(.*?)\"").getMatch(0);
            if (filename == null) {
                filename = br.getRegex("<h2 style=\\'clear: both;\\' align=\\'left\\'>(.*?)</h2>").getMatch(0);
            }
        }
        if (filename == null) {
            throw new PluginException(LinkStatus.ERROR_FILE_NOT_FOUND);
        }
        String Embed = br.getRegex("src=(?:\\'|\")(https?://(www\\.)?jizzhut\\.com/videos/embed/[0-9]+)(?:\\'|\")").getMatch(0);
        br.getPage(Embed);
        dllink = br.getRegex("addVariable\\(\"file\",.*?\"(https?://.*?\\.flv(\\?.*?)?)\"").getMatch(0);
        if (dllink == null) {
            dllink = br.getRegex("\"(https?://(mediax|cdn[a-z]\\.videos)\\.jizzhut\\.com/[A-Z0-9]+\\.flv(\\?.*?)?)\"").getMatch(0);
            if (dllink == null) {
                dllink = br.getRegex("'href','(https?://[^'<>]*?\\.(?:mp4|flv)(\\?.*?)?)'").getMatch(0);
                if (dllink == null) {
                    dllink = br.getRegex("<source src=\"([^\"]+)\"\\s*type=\"video/mp4\">").getMatch(0);
                    if (dllink != null) {
                        dllink = "http:" + dllink;
                    }
                }
            }
            if (dllink == null) {
                String playlist = br.getRegex("so\\.addVariable\\(\"playlist\", \"(https?://(www\\.)?(jizzhut|youjizz)\\.com/playlist\\.php\\?id=\\d+)").getMatch(0);
                if (playlist == null) {
                    throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
                }
                Browser br2 = br.cloneBrowser();
                br2.getPage(playlist);
                // multiple qualities (low|med|high) grab highest for now, decrypter will be needed for others.
                dllink = br2.getRegex("<level bitrate=\"\\d+\" file=\"(https?://(\\w+\\.){1,}(jizzhut|youjizz)\\.com/[^\"]+)\" ?></level>[\r\n\t ]+</levels>").getMatch(0);
                if (dllink != null) {
                    dllink = dllink.replace("%252", "%2");
                }
            }
        }
        if (dllink == null) {
            throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
        }
        Browser br2 = br.cloneBrowser();
        // In case the link redirects to the finallink
        br2.setFollowRedirects(true);
        URLConnectionAdapter con = null;
        try {
            con = br2.openGetConnection(dllink);
            if (!con.getContentType().contains("html")) {
                String ext = getFileNameFromHeader(con).substring(getFileNameFromHeader(con).lastIndexOf("."));
                downloadLink.setFinalFileName(Encoding.htmlDecode(filename) + ext);
                downloadLink.setDownloadSize(con.getLongContentLength());
            } else {
                throw new PluginException(LinkStatus.ERROR_FILE_NOT_FOUND);
            }
            return AvailableStatus.TRUE;
        } finally {
            try {
                con.disconnect();
            } catch (Throwable e) {
            }
        }
    }

    @Override
    public void reset() {
    }

    @Override
    public void resetDownloadlink(DownloadLink link) {
    }

    @Override
    public void resetPluginGlobals() {
    }
}