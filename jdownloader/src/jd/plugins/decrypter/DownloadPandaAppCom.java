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

package jd.plugins.decrypter;

import java.util.ArrayList;

import jd.PluginWrapper;
import jd.controlling.ProgressController;
import jd.http.Browser;
import jd.nutils.encoding.Encoding;
import jd.parser.Regex;
import jd.plugins.CryptedLink;
import jd.plugins.DecrypterPlugin;
import jd.plugins.DownloadLink;
import jd.plugins.FilePackage;
import jd.plugins.PluginForDecrypt;

@DecrypterPlugin(revision = "$Revision: 34675 $", interfaceVersion = 2, names = { "download.pandaapp.com" }, urls = { "http://(www\\.)?download\\.pandaapp\\.com/[^<>\"/]*?/[^<>\"/]*?\\-id\\d+\\.html" }) 
public class DownloadPandaAppCom extends PluginForDecrypt {

    public DownloadPandaAppCom(PluginWrapper wrapper) {
        super(wrapper);
    }

    public ArrayList<DownloadLink> decryptIt(CryptedLink param, ProgressController progress) throws Exception {
        ArrayList<DownloadLink> decryptedLinks = new ArrayList<DownloadLink>();
        String parameter = param.toString();
        br.setFollowRedirects(true);
        br.getPage(parameter);
        br.setFollowRedirects(false);
        if (br.getURL().equals("http://www.pandaapp.com/error/") || br.containsHTML("Sorry,this software does not exist or has been deleted") || br.toString().length() < 500) {
            logger.info("Link offline: " + parameter);
            final DownloadLink offline = createDownloadlink("directhttp://" + parameter);
            offline.setAvailable(false);
            offline.setProperty("offline", true);
            decryptedLinks.add(offline);
            return decryptedLinks;
        }
        // Check if there was a redirect to another site
        if (!br.getURL().contains("pandaapp.com/")) {
            decryptedLinks.add(createDownloadlink(br.getURL()));
            return decryptedLinks;
        }
        final String fpName = br.getRegex("<div class=\"title\">[\t\n\r ]+<h1>([^<>\"]*?)</h1>").getMatch(0);
        String[] links = br.getRegex("\\&target=(http[^<>\"]*?)\"").getColumn(0);
        if (links != null && links.length != 0) {
            for (String singleLink : links) {
                decryptedLinks.add(createDownloadlink(Encoding.htmlDecode(singleLink)));
            }
        }
        final String controller = br.getRegex("\\&controller=([^<>\"/]*?)\\&").getMatch(0);
        if (controller != null) {
            final Browser br2 = br.cloneBrowser();
            br2.getHeaders().put("X-Requested-With", "XMLHttpRequest");
            br2.getPage("http://download.pandaapp.com/?app=soft&controller=" + controller + "&action=FastDownAjaxRedirect&f_id=" + new Regex(parameter, "id(\\d+)\\.html$").getMatch(0));
            String finallink = br2.getRegex("\"url\":\"(http:[^<>\"]*?)\"").getMatch(0);
            if (finallink != null) {
                finallink = Encoding.htmlDecode(finallink.trim().replace("\\", ""));
                br2.getPage(finallink);
                finallink = br2.getRedirectLocation();
                if (finallink != null) {
                    decryptedLinks.add(createDownloadlink("directhttp://" + finallink));
                }
            }
        }
        if (decryptedLinks.size() == 0) {
            if (br.containsHTML(">The VIP members can freely use Fast Download")) {
                logger.info("This link is not downloadable for free users: " + parameter);
                return decryptedLinks;
            }
            logger.warning("Decrypter broken for link: " + parameter);
            return null;
        }
        if (fpName != null) {
            FilePackage fp = FilePackage.getInstance();
            fp.setName(Encoding.htmlDecode(fpName.trim()));
            fp.addLinks(decryptedLinks);
        }
        return decryptedLinks;
    }

    /* NO OVERRIDE!! */
    public boolean hasCaptcha(CryptedLink link, jd.plugins.Account acc) {
        return false;
    }

}