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

import jd.PluginWrapper;
import jd.http.Browser;
import jd.nutils.encoding.Encoding;
import jd.parser.Regex;
import jd.plugins.DownloadLink;
import jd.plugins.DownloadLink.AvailableStatus;
import jd.plugins.HostPlugin;
import jd.plugins.LinkStatus;
import jd.plugins.Plugin;
import jd.plugins.PluginException;
import jd.plugins.PluginForHost;
import jd.utils.locale.JDL;

import org.appwork.utils.formatter.SizeFormatter;

@HostPlugin(revision = "$Revision: 35923 $", interfaceVersion = 2, names = { "indowebster.com" }, urls = { "https?://(?:www\\.)?(files\\.)?(?:indowebster\\.com|idws\\.id)/(download/(files|audio|video)/.+|[^\\s]+\\.html)" })
public class Indowebster extends PluginForHost {

    private static final String PASSWORDTEXT = "(>THIS FILE IS PASSWORD PROTECTED<|>INSERT PASSWORD<|class=\"redbtn\" value=\"Unlock\"|method=\"post\" id=\"form_pass\")";

    public Indowebster(final PluginWrapper wrapper) {
        super(wrapper);
    }

    @Override
    public String getAGBLink() {
        return "http://www.indowebster.com/policy-tos.php";
    }

    private static final String NOCHUNKS = "NOCHUNKS";

    private String getDLLink() throws Exception {
        final Browser br2 = br.cloneBrowser();
        final Regex importantStuff = br.getRegex("\\$\\.post\\(\\'(http://[^<>\"]+)\\',\\{(.*?)\\}");
        final String action = importantStuff.getMatch(0);
        final String pagePiece = importantStuff.getMatch(1);
        if (action == null || pagePiece == null) {
            return null;
        }
        final String[] list = pagePiece.split(",");
        if (list == null || list.length == 0) {
            return null;
        }
        String post = "";
        for (final String str : list) {
            final String[] data = str.split(":");
            if (data.length != 2) {
                return null;
            }
            final String strg1 = data[0].replace("'", "").trim();
            final String strg2 = data[1].replace("'", "").trim();
            post += post.equals("") ? post : "&";
            post += strg1 + "=" + strg2.replace("checkCookie1()", "0");
        }
        br2.postPage(action, post);
        String dllink = br2.toString();
        if (dllink == null || !dllink.startsWith("http") || dllink.length() > 500) {
            return null;
        }
        dllink = dllink.replace("[", "%5B").replace("]", "%5D");
        return dllink;
    }

    @Override
    public int getMaxSimultanFreeDownloadNum() {
        return -1;
    }

    @Override
    public AvailableStatus requestFileInformation(final DownloadLink downloadLink) throws Exception {
        setBrowserExclusive();
        br.setFollowRedirects(true);
        br.setReadTimeout(3 * 60 * 1000);
        br.setConnectTimeout(3 * 60 * 1000);
        br.getPage(downloadLink.getDownloadURL());
        if (br.containsHTML("(Requested file is deleted|image/default/404\\.png\")") || br.getURL().contains("/error") || br.getURL().contains("/files_not_found") || br.containsHTML(">404 Page Not Found<")) {
            throw new PluginException(LinkStatus.ERROR_FILE_NOT_FOUND);
        }
        if (br.containsHTML(">404 Page Not Found<") || this.br.getHttpConnection().getResponseCode() == 404) {
            throw new PluginException(LinkStatus.ERROR_FILE_NOT_FOUND);
        }
        // Convert old links to new links
        String newlink = br.getRegex("<meta http\\-equiv=\"refresh\" content=\"\\d+;URL=(http://v\\d+\\.(?:indowebster\\.com|idws\\.id)/.*?)\"").getMatch(0);
        if (newlink != null) {
            newlink = newlink.trim();
            downloadLink.setUrlDownload(newlink);
            logger.info("New link set...");
            br.getPage(newlink);
        }
        String filename = br.getRegex("<h1 class=\"title\">(.*?)</h1>").getMatch(0);
        if (filename == null) {
            filename = br.getRegex("<meta name=\"title\" content=\"(.*?)\"").getMatch(0);
            if (filename == null) {
                filename = br.getRegex("<title>(.*?) \\- (?:Indowebster\\.com|idws\\.id)</title>").getMatch(0);
            }
        }
        String filesize = br.getRegex(">Size : <span style=\"float:none;\">(.*?)</span><").getMatch(0);
        if (filesize == null) {
            filesize = br.getRegex("Date upload: .{1,20} Size: (.*?)\"").getMatch(0);
            if (filesize == null) {
                filesize = br.getRegex("(?i)<strong>Size:</strong> ([\\d+\\.]+ ?(MB|GB))").getMatch(0);
            }
        }
        if (filename == null) {
            throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
        }
        if (downloadLink.getDownloadURL().contains("/audio/")) {
            downloadLink.setFinalFileName(Encoding.htmlDecode(filename.trim()) + ".mp3");
        } else {
            downloadLink.setName(Encoding.htmlDecode(filename.trim()));
        }
        if (filesize != null) {
            downloadLink.setDownloadSize(SizeFormatter.getSize(filesize));
        }
        if (br.containsHTML(PASSWORDTEXT)) {
            downloadLink.getLinkStatus().setStatusText(JDL.L("plugins.hoster.indowebstercom.passwordprotected", "This link is password protected"));
        }
        return AvailableStatus.TRUE;
    }

    @Override
    public void handleFree(final DownloadLink link) throws Exception {
        requestFileInformation(link);
        if (br.containsHTML("Storage Maintenance, Back Later")) {
            throw new PluginException(LinkStatus.ERROR_TEMPORARILY_UNAVAILABLE, "Storage maintenance", 60 * 60 * 1000l);
        }
        if (br.containsHTML(">404 Page Not Found<")) {
            throw new PluginException(LinkStatus.ERROR_FATAL, "Unknown server error (404)");
        }
        String passCode = link.getStringProperty("pass", null);
        if (br.containsHTML(PASSWORDTEXT)) {
            final String valueName = br.getRegex("type=\"password\" name=\"(.*?)\"").getMatch(0);
            if (valueName == null) {
                throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
            }
            if (passCode == null) {
                passCode = Plugin.getUserInput("Password?", link);
            }
            br.postPage(link.getDownloadURL(), valueName + "=" + Encoding.urlEncode(passCode));
            if (br.containsHTML(PASSWORDTEXT)) {
                throw new PluginException(LinkStatus.ERROR_RETRY, "Wrong password");
            }
        }
        String ad_url = br.getRegex("<a id=\"download\" href=\"(http://.*?)\"").getMatch(0);
        if (ad_url == null) {
            ad_url = br.getRegex("\"(http://v\\d+\\.(?:indowebster\\.com|idws\\.id)/downloads/jgjbcf/[a-z0-9]+)\"").getMatch(0);
        }
        if (ad_url == null) {
            logger.warning("ad_url is null!");
            throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
        }
        br.getPage(ad_url);
        final String realName = br.getRegex("<strong id=\"filename\">(\\[\\w+\\.(?:indowebster\\.com|idws\\.id)\\])?(.*?)</strong>").getMatch(1);
        if (realName != null) {
            link.setFinalFileName(Encoding.htmlDecode(realName));
        }
        /**
         * If we reach this line the password should be correct even if the download fails
         */
        if (passCode != null) {
            link.setProperty("pass", passCode);
        }
        final String waittime = br.getRegex("var s = (\\d+);").getMatch(0);
        int wait = 25;
        if (waittime != null) {
            wait = Integer.parseInt(waittime);
        }
        sleep(wait * 1001l, link);
        String dllink = getDLLink();
        if (dllink == null) {
            throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
        }
        dllink = dllink.trim();
        br.setDebug(true);
        br.setReadTimeout(180 * 1001);
        br.setConnectTimeout(180 * 1001);

        int maxChunks = 0;
        if (link.getBooleanProperty(Indowebster.NOCHUNKS, false)) {
            maxChunks = 1;
        }

        dl = jd.plugins.BrowserAdapter.openDownload(br, link, dllink, true, maxChunks);
        if (dl.getConnection().getContentType().contains("html")) {
            br.followConnection();
            if (br.containsHTML(">404 Not Found<")) {
                throw new PluginException(LinkStatus.ERROR_TEMPORARILY_UNAVAILABLE, "Server error", 60 * 60 * 1000l);
            }
            if (br.containsHTML(">Indowebster\\.com under maintenance")) {
                throw new PluginException(LinkStatus.ERROR_TEMPORARILY_UNAVAILABLE, JDL.L("plugins.hoster.indowebster.undermaintenance", "Under maintenance"), 30 * 60 * 1000l);
            }
            if (br.containsHTML("But Our Download Server Can be Accessed from Indonesia Only")) {
                throw new PluginException(LinkStatus.ERROR_FATAL, "Download Server Can be Accessed from Indonesia Only");
            }
            throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
        }
        if (!this.dl.startDownload()) {
            try {
                if (dl.externalDownloadStop()) {
                    return;
                }
            } catch (final Throwable e) {
            }
            /* unknown error, we disable multiple chunks */
            if (link.getBooleanProperty(Indowebster.NOCHUNKS, false) == false) {
                link.setProperty(Indowebster.NOCHUNKS, Boolean.valueOf(true));
                throw new PluginException(LinkStatus.ERROR_RETRY);
            }
        }
    }

    @Override
    public void reset() {
    }

    @Override
    public void resetDownloadlink(final DownloadLink link) {
    }

}