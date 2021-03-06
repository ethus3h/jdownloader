//jDownloader - Downloadmanager
//Copyright (C) 2012  JD-Team support@jdownloader.org
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

import java.io.IOException;

import jd.PluginWrapper;
import jd.plugins.DownloadLink;
import jd.plugins.DownloadLink.AvailableStatus;
import jd.plugins.HostPlugin;
import jd.plugins.LinkStatus;
import jd.plugins.PluginException;
import jd.plugins.PluginForHost;
import jd.plugins.components.PluginJSonUtils;

import org.appwork.utils.formatter.SizeFormatter;

@HostPlugin(revision = "$Revision: 35904 $", interfaceVersion = 2, names = { "hightail.com" }, urls = { "http(s)?://(www\\.)?yousenditdecrypted\\.com/download/[A-Za-z0-9]+" })
public class HightailCom extends PluginForHost {

    private String DLLINK = null;

    public HightailCom(PluginWrapper wrapper) {
        super(wrapper);
    }

    @Override
    public String getAGBLink() {
        return "https://www.hightail.com/aboutus/legal/terms-of-service";
    }

    @SuppressWarnings("deprecation")
    public void correctDownloadLink(DownloadLink link) {
        link.setUrlDownload(link.getDownloadURL().replace("yousenditdecrypted.com/", "yousendit.com/"));
    }

    @Override
    public int getMaxSimultanFreeDownloadNum() {
        return -1;
    }

    @Override
    public String rewriteHost(String host) {
        if ("yousendit.com".equals(getHost())) {
            if (host == null || "yousendit.com".equals(host)) {
                return "hightail.com";
            }
        }
        return super.rewriteHost(host);
    }

    @Override
    public AvailableStatus requestFileInformation(final DownloadLink link) throws IOException, PluginException {
        this.setBrowserExclusive();
        if (link.getBooleanProperty("offline", false)) {
            throw new PluginException(LinkStatus.ERROR_FILE_NOT_FOUND);
        }
        br.setFollowRedirects(true);
        br.getPage(link.getStringProperty("mainlink", null));
        // File offline
        if (br.containsHTML("Download link is invalid|>Access has expired<|class=\"fileIcons disabledFile\"|/file_icon_error\\.png") || this.br.getHttpConnection().getResponseCode() == 404) {
            throw new PluginException(LinkStatus.ERROR_FILE_NOT_FOUND);
        } else if (br.containsHTML("file_icon_lock\\.png\"")) {
            /* File abused?! */
            throw new PluginException(LinkStatus.ERROR_FILE_NOT_FOUND);
        }
        if (link.getStringProperty("fileurl", null) != null || link.getStringProperty("fileurl_new", null) != null) {
            link.setFinalFileName(link.getStringProperty("directname", null));
            link.setDownloadSize(SizeFormatter.getSize(link.getStringProperty("directsize", null)));
        } else {
            final String filename = br.getRegex("id=\"downloadSingleFilename\"[^<>]*?>([^<>\"]*?)</span>").getMatch(0);
            final String filesize = br.getRegex("id=\"downloadSingleFilesize\">([^<>\"]*?)<span>").getMatch(0);
            if (filename == null) {
                logger.warning("hightail.com: Can't find filename, Please report this to the JD Developement team!");
                throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
            }
            if (filesize == null) {
                logger.info("hightail.com: Can't find filesize, Please report this to the JD Developement team!");
                logger.info("hightail.com: Continuing...");
            }
            link.setFinalFileName(filename.trim());
            if (filesize != null) {
                link.setDownloadSize(SizeFormatter.getSize(filesize));
            }
        }
        return AvailableStatus.TRUE;
    }

    @Override
    public void handleFree(final DownloadLink downloadLink) throws Exception, PluginException {
        requestFileInformation(downloadLink);
        final String fileurl_new = downloadLink.getStringProperty("fileurl_new", null);
        final String fileurl = downloadLink.getStringProperty("fileurl", null);
        if (fileurl_new != null) {
            br.getPage("https://de.hightail.com/folders?phi_action=app/directDownloadWorkspace&getResult=1&dlFileName=&fId=" + fileurl_new);
            DLLINK = PluginJSonUtils.getJsonValue(br, "downloadLink");
            if (DLLINK == null) {
                throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
            }
        } else if (fileurl != null) {
            DLLINK = "https://www.hightail.com/e?phi_action=app/directDownload&fl=" + fileurl;
        } else {
            if (DLLINK == null) {
                DLLINK = br.getRegex("class=\"btn\\-save\" href=\"([^<>\"]*?)\"").getMatch(0);
                if (DLLINK == null) {
                    DLLINK = br.getRegex("\"(e\\?phi_action=app/directDownload[^<>\"]*?)\"").getMatch(0);
                }
                if (DLLINK == null) {
                    throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
                }
                if (!DLLINK.contains("hightail.com")) {
                    DLLINK = "https://www.hightail.com/" + DLLINK;
                }
            }
        }
        dl = jd.plugins.BrowserAdapter.openDownload(br, downloadLink, DLLINK, true, 0);
        if (dl.getConnection().getContentType().contains("html")) {
            br.followConnection();
            if (this.br.containsHTML("An Error Has Occurred<")) {
                throw new PluginException(LinkStatus.ERROR_TEMPORARILY_UNAVAILABLE, "Server error", 10 * 60 * 1000l);
            }
            throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
        }
        dl.startDownload();
    }

    @Override
    public void reset() {
    }

    @Override
    public void resetDownloadlink(DownloadLink link) {
    }

}