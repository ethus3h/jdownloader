//jDownloader - Downloadmanager
//Copyright (C) 2013  JD-Team support@jdownloader.org
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
import java.util.regex.Pattern;

import org.jdownloader.plugins.components.antiDDoSForDecrypt;

import jd.PluginWrapper;
import jd.controlling.ProgressController;
import jd.nutils.encoding.Encoding;
import jd.plugins.CryptedLink;
import jd.plugins.DecrypterPlugin;
import jd.plugins.DownloadLink;
import jd.plugins.FilePackage;

@DecrypterPlugin(revision = "$Revision: 34785 $", interfaceVersion = 2, names = {}, urls = {})
public class GogoanimeCom extends antiDDoSForDecrypt {

    /**
     * Returns the annotations names array
     *
     * @return
     */
    public static String[] getAnnotationNames() {
        return new String[] { "gogoanime.com", "goodanime.net", "gogoanime.to", "gooddrama.net", "playbb.me", "videowing.me", "easyvideo.me", "videozoo.me", "video66.org", "animewow.tv", "dramago.com", "playpanda.net", "byzoo.org", "vidzur.com", "animetoon.tv", "dramagalaxy.com", "toonget.com", "goodmanga.net" };
    }

    /**
     * returns the annotation pattern array
     *
     * @return
     */
    public static String[] getAnnotationUrls() {
        String[] a = new String[getAnnotationNames().length];
        int i = 0;
        for (final String domain : getAnnotationNames()) {
            a[i] = "http://(?:www\\.)?" + Pattern.quote(domain) + "/(?!flowplayer)(?:embed(\\.php)?\\?.*?vid(?:eo)?=.+|gogo/\\?.*?file=.+|(?:(?:[a-z\\-]+\\-drama|[a-z\\-]+\\-movie)/)?[a-z0-9\\-_]+(?:/\\d+)?)";
            i++;
        }
        return a;
    }

    // NOTE:
    // play44.net = gogoanime.com url (doesn't seem to have mirror in its own domain happening)
    // videobug.net = gogoanime.com url (doesn't seem to have mirror in its own domain happening)

    public GogoanimeCom(PluginWrapper wrapper) {
        super(wrapper);
    }

    private final String invalidLinks = ".+" + Pattern.quote(this.getHost()) + "/(category|thumbs|sitemap|img|xmlrpc|fav|images|ads|gga\\-contact).*?";
    private final String embed        = ".+" + Pattern.quote(this.getHost()) + "/(embed(\\.php)?\\?.*?vid(eo)?=.+|gogo/\\?.*?file=.+)";

    @Override
    protected boolean useRUA() {
        return true;
    }

    public ArrayList<DownloadLink> decryptIt(CryptedLink param, ProgressController progress) throws Exception {
        ArrayList<DownloadLink> decryptedLinks = new ArrayList<DownloadLink>();
        String parameter = param.toString();
        if (parameter.matches(invalidLinks)) {
            logger.info("Link invalid: " + parameter);
            return decryptedLinks;
        }
        br.setFollowRedirects(true);
        getPage(parameter);
        // Offline
        if (br.containsHTML("Oops\\! Page Not Found<|>404 Not Found<|Content has been removed due to copyright or from users\\.<") || this.br.getHttpConnection().getResponseCode() == 404 || this.br.getHttpConnection().getResponseCode() == 403) {
            logger.info("This link is offline: " + parameter);
            decryptedLinks.add(this.createOfflinelink(parameter));
            return decryptedLinks;
        }
        // Invalid link
        if (br.containsHTML("No htmlCode read")) {
            logger.info("This link is invalid: " + parameter);
            decryptedLinks.add(this.createOfflinelink(parameter));
            return decryptedLinks;
        }

        if (parameter.matches(embed)) {
            // majority, if not all are located on play44.net (or ip address). There for there is no need for many hoster plugins, best to
            // use single hoster plugin so connection settings are aok.
            final String url = br.getRegex(".+url: (\"|')(.+\\.(mp4|flv|avi|mpeg|mkv).*?)\\1").getMatch(1);
            if (url != null) {
                final DownloadLink link = createDownloadlink(Encoding.htmlDecode(url));
                if (link != null) {
                    link.setProperty("forcenochunkload", Boolean.TRUE);
                    link.setProperty("forcenochunk", Boolean.TRUE);
                    decryptedLinks.add(link);
                }
            }
        } else {
            String fpName = br.getRegex("<h1( class=\"generic\">|>[^\r\n]+)(.*?)</h1>").getMatch(1);
            if (fpName == null || fpName.length() == 0) {
                fpName = br.getRegex("<title>([^<>\"]*?)( \\w+ Sub.*?|\\s*\\|\\s* Watch anime online, English anime online)?</title>").getMatch(0);
            }

            final String[] links = br.getRegex("<iframe.*?src=(\"|\\')(http[^<>\"]+)\\1").getColumn(1);
            if (links == null || links.length == 0) {
                logger.warning("Decrypter broken for link: " + parameter);
                return null;
            }
            for (String singleLink : links) {
                // lets prevent returning of links which contain itself.
                if (!singleLink.matches(".+(" + Pattern.quote(this.getHost()) + "|imgur\\.com).+|.+broken\\.png|.+counter\\.js")) {
                    singleLink = Encoding.htmlDecode(singleLink);
                    final DownloadLink dl = createDownloadlink(singleLink);
                    if (dl != null) {
                        dl.setProperty("forcenochunkload", Boolean.TRUE);
                        dl.setProperty("forcenochunk", Boolean.TRUE);
                        decryptedLinks.add(dl);
                    }
                }
            }
            if (fpName != null) {
                final FilePackage fp = FilePackage.getInstance();
                fp.setName(fpName.trim());
                fp.addLinks(decryptedLinks);
                fp.setProperty("ALLOW_MERGE", true);
            }
        }
        return decryptedLinks;
    }

    /* NO OVERRIDE!! */
    public boolean hasCaptcha(CryptedLink link, jd.plugins.Account acc) {
        return false;
    }

}