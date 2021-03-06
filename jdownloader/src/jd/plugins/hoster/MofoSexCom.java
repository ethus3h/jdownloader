//jDownloader - Downloadmanager
//Copyright (C) 2009  JD-Team support@jdownloader.org
//
//This program is free software: you can redistribute it and/or modify
//it under the terms of the GNU General Public License as published by
//the Free Software Foundation, either version 3 of the License, or
//(at your option) any later version.
//
//This program is distributed in the hope that it will be useful,
//but WITHOUT ANY WARRANTY; without even the implied warranty of
//MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
//GNU General Public License for more details.
//
//You should have received a copy of the GNU General Public License
//along with this program.  If not, see <http://www.gnu.org/licenses/>.

package jd.plugins.hoster;

import org.jdownloader.plugins.components.antiDDoSForHost;

import jd.PluginWrapper;
import jd.http.Browser;
import jd.http.URLConnectionAdapter;
import jd.nutils.encoding.Encoding;
import jd.parser.Regex;
import jd.plugins.DownloadLink;
import jd.plugins.DownloadLink.AvailableStatus;
import jd.plugins.HostPlugin;
import jd.plugins.LinkStatus;
import jd.plugins.PluginException;

/**
 * DEV NOTES:<br/>
 * - related to keezmovies, same group of sites. Tells: incapsula and phncdn.com CDN -raztoki
 *
 */
@HostPlugin(revision = "$Revision: 34675 $", interfaceVersion = 3, names = { "mofosex.com" }, urls = { "http://(www\\.)?mofosex\\.com/(videos/\\d+/[a-z0-9\\-]+\\.html|embed\\?videoid=\\d+|embed_player\\.php\\?id=\\d+)" }) 
public class MofoSexCom extends antiDDoSForHost {

    private String DLLINK = null;

    public MofoSexCom(final PluginWrapper wrapper) {
        super(wrapper);
    }

    @Override
    public String getAGBLink() {
        return "http://www.mofosex.com/terms-of-use.php";
    }

    @Override
    public int getMaxSimultanFreeDownloadNum() {
        return -1;
    }

    private static final String TYPE_EMBED = "http://(www\\.)?mofosex\\.com/(embed\\?videoid=|embed_player\\.php\\?id=)\\d+";

    @SuppressWarnings("deprecation")
    @Override
    public AvailableStatus requestFileInformation(final DownloadLink downloadLink) throws Exception {
        DLLINK = null;
        setBrowserExclusive();
        br.setFollowRedirects(true);
        if (downloadLink.getDownloadURL().matches(TYPE_EMBED)) {
            logger.info("Handling embedded url...");
            final String linkid = new Regex(downloadLink.getDownloadURL(), "(\\d+)$").getMatch(0);
            /* Offline links should get good filenames too */
            downloadLink.setName(linkid);
            getPage("http://www.mofosex.com/embed?videoid=" + linkid);
            if (br.containsHTML("This video is no longer available")) {
                throw new PluginException(LinkStatus.ERROR_FILE_NOT_FOUND);
            }
            String real_url = br.getRegex("link_url=(http%3A%2F%2F(www\\.)?mofosex\\.com%2Fvideos%2F[^<>\"/]*?\\.html)\\&amp;").getMatch(0);
            if (real_url == null) {
                real_url = br.getRegex("class=\"footer\"><br/><a href=\"([^<>\"]*?)\"").getMatch(0);
            }
            if (real_url == null) {
                throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
            }
            if (real_url.startsWith("/")) {
                real_url = "http://" + this.br.getHost() + real_url;
            }
            real_url = Encoding.htmlDecode(real_url);
            downloadLink.setUrlDownload(real_url);
            getPage(real_url);
        } else {
            getPage(downloadLink.getDownloadURL());
            if (br.containsHTML("(<h2>The porn you are looking for has been removed|<title>Free Porn Videos, Porn Tube, Sex Videos, Sex \\&amp; Free XXX Porno Clips</title>|>Page Not Found<|This video is no longer available|video\\-removed\\-tos\\.png\")") || br.getURL().contains("/404.php")) {
                throw new PluginException(LinkStatus.ERROR_FILE_NOT_FOUND);
            }
        }
        String filename = br.getRegex("flashvars\\.video_title = \"([^<>\"]*?)\"").getMatch(0);
        if (filename == null) {
            filename = br.getRegex("<title>(.*?) Videos \\- Mofosex\\.com</title>").getMatch(0);
        }
        String fid = br.getRegex("\\?v=([a-z0-9_\\-]+)%2").getMatch(0);
        if (fid != null) {
            getPage("http://www.mofosex.com/playlist.php?v=" + fid);
            DLLINK = br.getRegex("<url>(http://.*?)</url>").getMatch(0);
        } else {
            fid = br.getRegex("flashvars\\.video_url = \'(.*?)\'").getMatch(0);
            DLLINK = fid != null ? fid : null;
        }
        if (filename == null || DLLINK == null) {
            throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
        }
        DLLINK = Encoding.htmlDecode(DLLINK);
        filename = filename.trim();
        downloadLink.setFinalFileName(Encoding.htmlDecode(filename) + ".flv");
        final Browser br2 = br.cloneBrowser();
        // In case the link redirects to the finallink
        br2.setFollowRedirects(true);
        URLConnectionAdapter con = null;
        try {
            con = br2.openHeadConnection(DLLINK);
            if (!con.getContentType().contains("html")) {
                downloadLink.setDownloadSize(con.getLongContentLength());
            } else {
                throw new PluginException(LinkStatus.ERROR_FILE_NOT_FOUND);
            }
            return AvailableStatus.TRUE;
        } finally {
            try {
                con.disconnect();
            } catch (final Throwable e) {
            }
        }
    }

    @Override
    public void handleFree(final DownloadLink downloadLink) throws Exception {
        requestFileInformation(downloadLink);
        dl = jd.plugins.BrowserAdapter.openDownload(br, downloadLink, DLLINK, true, 0);
        if (dl.getConnection().getContentType().contains("html")) {
            br.followConnection();
            throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
        }
        dl.startDownload();
    }

    @Override
    public void reset() {
    }

    @Override
    public void resetDownloadlink(final DownloadLink link) {
    }

    @Override
    public void resetPluginGlobals() {
    }
}