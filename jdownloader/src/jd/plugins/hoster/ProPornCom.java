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

import java.util.Map;

import org.jdownloader.scripting.JavaScriptEngineFactory;

import jd.PluginWrapper;
import jd.http.Browser;
import jd.http.Browser.BrowserException;
import jd.http.URLConnectionAdapter;
import jd.nutils.encoding.Encoding;
import jd.parser.Regex;
import jd.plugins.DownloadLink;
import jd.plugins.DownloadLink.AvailableStatus;
import jd.plugins.HostPlugin;
import jd.plugins.LinkStatus;
import jd.plugins.PluginException;
import jd.plugins.PluginForHost;
import jd.plugins.components.PluginJSonUtils;

@HostPlugin(revision = "$Revision: 35544 $", interfaceVersion = 3, names = { "proporn.com" }, urls = { "http://((www|de|fr|ru|es|it|jp|nl|pl|pt)\\.)?proporn\\.com/(video|embed)/\\d+" })
public class ProPornCom extends PluginForHost {

    public ProPornCom(PluginWrapper wrapper) {
        super(wrapper);
    }

    private static final String SKEY   = "VjN0NlJLRlkxMU9RNEo0Ug==";
    private String              dllink = null;

    @Override
    public String getAGBLink() {
        return "http://www.proporn.com/static/terms";
    }

    @SuppressWarnings("deprecation")
    public void correctDownloadLink(final DownloadLink link) {
        link.setUrlDownload("http://www.proporn.com/video/" + new Regex(link.getDownloadURL(), "(\\d+)$").getMatch(0));
    }

    /* Similar sites: drtuber.com, proporn.com, viptube.com, tubeon.com, winporn.com */
    /* IMPORTANT: If the crypto stuff fails, use the mobile version of the sites to get uncrypted finallinks! */
    @SuppressWarnings("deprecation")
    @Override
    public AvailableStatus requestFileInformation(final DownloadLink downloadLink) throws Exception {
        this.setBrowserExclusive();
        br.setFollowRedirects(true);
        br.setCookie("http://proporn.com/", "lang", "en");
        br.getPage(downloadLink.getDownloadURL());
        if (br.containsHTML("class=\"nothing\"|<title>Page not found</title>")) {
            throw new PluginException(LinkStatus.ERROR_FILE_NOT_FOUND);
        }
        String filename = br.getRegex("<title>([^<>\"]*?)\\- Free Porn Video").getMatch(0);
        final String uid = PluginJSonUtils.getJson(br, "vid");
        final Browser ajax = br.cloneBrowser();
        ajax.getHeaders().put("Accept", "application/json, text/javascript, */*; q=0.01");
        ajax.getHeaders().put("X-Requested-With", "XMLHttpRequest");
        ajax.getPage("/player_config_json/?vid=" + uid + "&aid=&domain_id=&embed=0&ref=&check_speed=0");
        final Map<String, Object> map = (Map<String, Object>) JavaScriptEngineFactory.jsonToJavaObject(ajax.toString());
        if (filename == null) {
            filename = (String) map.get("title");
        }
        dllink = (String) JavaScriptEngineFactory.walkJson(map, "files/hq");
        if (dllink == null) {
            dllink = (String) JavaScriptEngineFactory.walkJson(map, "files/lq");
        }
        if (filename == null || dllink == null) {
            throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
        }
        dllink = Encoding.htmlDecode(dllink);
        filename = filename.trim();
        final String ext = getFileNameExtensionFromString(dllink, ".mp4");
        downloadLink.setFinalFileName(Encoding.htmlDecode(filename) + ext);
        final Browser br2 = br.cloneBrowser();
        // In case the link redirects to the finallink
        br2.setFollowRedirects(true);
        URLConnectionAdapter con = null;
        try {
            try {
                con = br2.openHeadConnection(dllink);
                /*
                 * Small workaround for buggy servers that redirect and fail if the Referer is wrong then. Examples: hdzog.com
                 */
                final String redirect_url = con.getRequest().getUrl();
                con = br.openHeadConnection(redirect_url);
            } catch (final BrowserException e) {
                throw new PluginException(LinkStatus.ERROR_FILE_NOT_FOUND);
            }
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
        dl = jd.plugins.BrowserAdapter.openDownload(br, downloadLink, dllink, true, 0);
        if (dl.getConnection().getContentType().contains("html")) {
            br.followConnection();
            throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
        }
        dl.startDownload();
    }

    @Override
    public int getMaxSimultanFreeDownloadNum() {
        return -1;
    }

    @Override
    public void reset() {
    }

    @Override
    public void resetPluginGlobals() {
    }

    @Override
    public void resetDownloadlink(DownloadLink link) {
    }
}
