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

import java.io.IOException;
import java.util.concurrent.atomic.AtomicReference;

import jd.PluginWrapper;
import jd.http.Browser;
import jd.parser.Regex;
import jd.plugins.DownloadLink;
import jd.plugins.DownloadLink.AvailableStatus;
import jd.plugins.HostPlugin;
import jd.plugins.LinkStatus;
import jd.plugins.PluginException;
import jd.plugins.PluginForHost;

import org.appwork.utils.formatter.SizeFormatter;

@HostPlugin(revision = "$Revision: 34675 $", interfaceVersion = 2, names = { "uploadmb.com" }, urls = { "http://[\\w\\.]*?uploadmb\\.com/dw\\.php\\?id=\\d+" }) 
public class UploadMbCom extends PluginForHost {

    public UploadMbCom(PluginWrapper wrapper) {
        super(wrapper);
    }

    public void correctDownloadLink(DownloadLink link) {
        // Remove unneeded stuff that could cause errors
        fuid = new Regex(link.getDownloadURL(), "uploadmb\\.com/dw\\.php\\?id=(\\d+)").getMatch(0);
        link.setUrlDownload("http://www.uploadmb.com/dw.php?id=" + fuid);
    }

    @Override
    public String getAGBLink() {
        return "http://uploadmb.com/termsconditions.php";
    }

    @Override
    public int getMaxSimultanFreeDownloadNum() {
        return -1;
    }

    private final static AtomicReference<String> PHPSESSID = new AtomicReference<String>(null);
    private final static AtomicReference<String> userAgent = new AtomicReference<String>(null);
    private String                               fuid      = null;

    @Override
    public AvailableStatus requestFileInformation(final DownloadLink link) throws IOException, PluginException {
        br = new Browser();
        correctDownloadLink(link);
        this.setBrowserExclusive();
        // without phpsession cookie, it will redirect to some bitcoin advertising (scam?).
        getPhpSesId(link.getDownloadURL());
        if (br.containsHTML("(>The file you are requesting to download is not available<br>|Reasons for this \\(Invalid link, Violation of <a|The file you are requesting to download is not available)")) {
            throw new PluginException(LinkStatus.ERROR_FILE_NOT_FOUND);
        }
        Regex name2AndSize = br.getRegex("\\&#100;\\&#58;</font></b> (.*?)\\(([\\d\\.]+ [a-zA-Z]+)\\)<br>");
        String filename = br.getRegex("addthis_title  = \\'(.*?)\\';").getMatch(0);
        if (filename == null) {
            filename = name2AndSize.getMatch(0);
        }
        String filesize = name2AndSize.getMatch(1);
        if (filename == null) {
            throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
        }
        // Because server often gives back wrong names
        link.setFinalFileName(filename.trim());
        if (filesize != null) {
            link.setDownloadSize(SizeFormatter.getSize(filesize));
        }
        return AvailableStatus.TRUE;
    }

    @Override
    public void handleFree(final DownloadLink downloadLink) throws Exception, PluginException {
        requestFileInformation(downloadLink);
        String specialStuff = br.getRegex("value=\"(\\d+)\" name=\"turingno\"").getMatch(0);
        if (specialStuff == null) {
            specialStuff = "5";
        }
        br.postPage(downloadLink.getDownloadURL(), "turingno=" + specialStuff + "&id=" + new Regex(downloadLink.getDownloadURL(), "dw\\.php\\?id=(\\d+)").getMatch(0) + "&DownloadNow=Download+File&PHPSESSID=" + br.getCookie("http://uploadmb.com/", "PHPSESSID"));
        String dllink = br.getRegex("or <a href=\\'(/.*?)\\'").getMatch(0);
        if (dllink == null) {
            dllink = br.getRegex("(\\'|\")(/file\\.php\\?id=\\d+\\&/.*?)\\1").getMatch(1);
        }
        if (dllink == null) {
            throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
        }
        dl = jd.plugins.BrowserAdapter.openDownload(br, downloadLink, dllink, false, 1);
        if (dl.getConnection().getContentType().contains("html")) {
            br.followConnection();
            if (br.containsHTML("Too many loads")) {
                throw new PluginException(LinkStatus.ERROR_TEMPORARILY_UNAVAILABLE, "Server error");
            }
            throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
        }
        dl.startDownload();
    }

    private void getPhpSesId(final String page) throws IOException, PluginException {
        br.setFollowRedirects(false);
        if (PHPSESSID.get() != null) {
            br.getHeaders().put("User-Agent", userAgent.get());
            br.setCookie(this.getHost(), "PHPSESSID", PHPSESSID.get());
            br.getPage(page);
            final String redirect = br.getRedirectLocation();
            if (redirect == null) {
                return;
            }
            if (redirect != null) {
                // some adveritsing bullshit when phpsessid == null
                if (redirect.matches(".+uploadmb\\.com/dvv\\.php\\?id=" + fuid)) {
                    PHPSESSID.set(null);
                    br = new Browser();
                } else {
                    br.getPage(redirect);
                    return;
                }
            }
        }
        // set a new user-agent
        userAgent.set(jd.plugins.hoster.MediafireCom.stringUserAgent());
        br.getHeaders().put("User-Agent", userAgent.get());
        br.getPage(page);
        final String redirect = br.getRedirectLocation();
        if (redirect != null) {
            // some adveritsing bullshit when phpsessid == null
            if (redirect.matches(".+uploadmb\\.com/dvv\\.php\\?id=" + fuid)) {
                br.getPage(redirect);
            }
        }
        final String phpsessid = br.getCookie(this.getHost(), "PHPSESSID");
        if (phpsessid == null) {
            throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
        }
        PHPSESSID.set(phpsessid);
        if (!br.getURL().equals(page)) {
            br.getPage(page);
        }
    }

    @Override
    public void reset() {
    }

    @Override
    public void resetDownloadlink(DownloadLink link) {
    }

}