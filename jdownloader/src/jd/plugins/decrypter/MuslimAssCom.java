//jDownloader - Downloadmanager
//Copyright (C) 2011  JD-Team support@jdownloader.org
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
import jd.nutils.encoding.Encoding;
import jd.parser.Regex;
import jd.parser.html.HTMLParser;
import jd.plugins.CryptedLink;
import jd.plugins.DecrypterPlugin;
import jd.plugins.DownloadLink;
import jd.plugins.FilePackage;
import jd.plugins.PluginForDecrypt;

@DecrypterPlugin(revision = "$Revision: 35545 $", interfaceVersion = 2, names = { "muslimass.com" }, urls = { "http://(www\\.)?muslimass\\.com/(?!wp)[a-z0-9]+\\-[a-z0-9\\-]+" })
public class MuslimAssCom extends PluginForDecrypt {

    public MuslimAssCom(PluginWrapper wrapper) {
        super(wrapper);
    }

    // This is a site which shows embedded videos of other sites so we may have
    // to add regexes/handlings here
    public ArrayList<DownloadLink> decryptIt(CryptedLink param, ProgressController progress) throws Exception {
        final ArrayList<DownloadLink> decryptedLinks = new ArrayList<DownloadLink>();
        final String parameter = param.toString();
        br.setFollowRedirects(true);
        br.getPage(parameter);
        if (br.containsHTML(">Error 404 \\- Not Found<|>Nothing found for")) {
            logger.info("Link offline: " + parameter);
            return decryptedLinks;
        }
        String filename = br.getRegex("<link rel=\"alternate\" type=\"application/rss\\+xml\" title=\"muslimass\\.com \\&raquo; (.*?) Comments Feed\" href=\"").getMatch(0);
        if (filename == null) {
            filename = br.getRegex("title=\"Permanent Link to (.*?)\"").getMatch(0);
        }
        if (filename == null) {
            logger.warning("muslimass decrypter broken(filename regex) for link: " + parameter);
            return null;
        }
        filename = Encoding.htmlDecode(filename.trim());
        String externID = br.getRegex("\"id_video=(\\d+)\"").getMatch(0);
        if (externID != null) {
            String finallink = "http://www.xvideos.com/video" + externID + "/";
            decryptedLinks.add(createDownloadlink(Encoding.htmlDecode(finallink)));
            return decryptedLinks;
        }
        externID = br.getRegex("hardsextube\\.com/embed/(\\d+)/\"").getMatch(0);
        if (externID != null) {
            decryptedLinks.add(createDownloadlink("http://www.hardsextube.com/video/" + externID + "/"));
            return decryptedLinks;
        }
        externID = br.getRegex("hardsextube\\.com/embed/(\\d+)/\"").getMatch(0);
        if (externID != null) {
            decryptedLinks.add(createDownloadlink("http://www.hardsextube.com/video/" + externID + "/"));
            return decryptedLinks;
        }
        externID = br.getRegex("\"(http://embed\\.videarn\\.com/embed\\.php\\?id=\\d+)\"").getMatch(0);
        if (externID != null) {
            decryptedLinks.add(createDownloadlink(externID));
            return decryptedLinks;
        }
        // For direct hosterlinks - make sure only to grab the links of the related post
        final String pagePiece = br.getRegex("<div class=\"entry\">(.*?)>Share:\\&nbsp;</strong>").getMatch(0);
        if (pagePiece != null) {
            String fpName = br.getRegex("<title>([^<>\"]*?) \\| muslimass\\.com</title>").getMatch(0);
            if (fpName == null) {
                fpName = new Regex(parameter, "muslimass\\.com/(.+)").getMatch(0);
            }
            final String[] allLinks = HTMLParser.getHttpLinks(pagePiece, "");
            if (allLinks == null || allLinks.length == 0) {
                logger.info("Link offline: " + parameter);
                return decryptedLinks;
            }
            for (final String aLink : allLinks) {
                if (!aLink.matches("http://(www\\.)?muslimass\\.com/.+")) {
                    decryptedLinks.add(createDownloadlink(aLink));
                }
            }
            final FilePackage fp = FilePackage.getInstance();
            fp.setName(Encoding.htmlDecode(fpName.trim()));
            fp.addLinks(decryptedLinks);
            return decryptedLinks;
        }
        logger.warning("muslimass decrypter broken for link: " + parameter);
        return null;
    }

    /* NO OVERRIDE!! */
    public boolean hasCaptcha(CryptedLink link, jd.plugins.Account acc) {
        return false;
    }

}