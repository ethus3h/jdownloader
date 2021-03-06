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

package jd.plugins.decrypter;

import java.util.ArrayList;
import java.util.regex.Pattern;

import jd.PluginWrapper;
import jd.controlling.ProgressController;
import jd.http.Browser;
import jd.parser.Regex;
import jd.plugins.CryptedLink;
import jd.plugins.DecrypterPlugin;
import jd.plugins.DownloadLink;
import jd.plugins.FilePackage;
import jd.plugins.PluginForDecrypt;
import jd.plugins.components.UserAgents;

@DecrypterPlugin(revision = "$Revision: 34890 $", interfaceVersion = 2, names = { "mirrorcreator.com" }, urls = { "https?://(www\\.)?(mirrorcreator\\.com/(files/|download\\.php\\?uid=)|mir\\.cr/)[0-9A-Z]{8}" })
public class MirrorCreatorCom extends PluginForDecrypt {

    private String userAgent = null;

    public MirrorCreatorCom(PluginWrapper wrapper) {
        super(wrapper);
    }

    public ArrayList<DownloadLink> decryptIt(CryptedLink param, ProgressController progress) throws Exception {
        br = new Browser();
        final String uid = new Regex(param.toString(), "([A-Z0-9]{8})$").getMatch(0);
        final ArrayList<DownloadLink> decryptedLinks = new ArrayList<DownloadLink>();
        if (userAgent == null) {
            userAgent = UserAgents.stringUserAgent();
        }
        br.getHeaders().put("User-Agent", userAgent);
        final String parameter = "https://www.mirrorcreator.com/download.php?uid=" + uid;
        param.setCryptedUrl(parameter);

        br.setFollowRedirects(true);
        br.getPage(parameter);
        br.setFollowRedirects(false);
        // set packagename
        // because mirror creator is single file uploader. we want a single packagename for all these uploads vs one for each part!
        String fpName = br.getRegex("<title>Download links for ([^<]+) - Mirrorcreator").getMatch(0);
        if (fpName != null) {
            // here we will strip extensions!
            String ext;
            do {
                ext = getFileNameExtensionFromString(fpName);
                if (ext != null) {
                    fpName = fpName.replaceFirst(Pattern.quote(ext) + "$", "");
                }
            } while (ext != null);
        }
        final FilePackage fp = fpName != null ? FilePackage.getInstance() : null;
        if (fp != null) {
            fp.setName(fpName);
            fp.setProperty("AllOW_MERGE", true);
        }
        String continuelink = br.getRegex("\"(/mstats?\\.php\\?uid=" + uid + "&[^\"]+=[a-f0-9]{32})\"").getMatch(0);
        if (continuelink != null) {
            br.getPage(continuelink);
        }
        /* Error handling */
        if (br.containsHTML("(>Unfortunately, the link you have clicked is not available|>Error - Link disabled or is invalid|>Links Unavailable as the File Belongs to Suspended Account\\. <|>Links Unavailable\\.<)")) {
            logger.info("The following link should be offline: " + param.toString());
            return decryptedLinks;
        }

        // they comment in fakes, so we will just try them all!
        String[] links = br.getRegex("(/[^<>\"/]*?=[a-z0-9]{25,32})\"").getColumn(0);
        if (links == null || links.length == 0) {
            links = br.getRegex("\"(/hosts/" + uid + "[^\"]+)\"").getColumn(0);
            if (links == null || links.length == 0) {
                logger.warning("A critical error happened! Please inform the support. : " + param.toString());
                return null;
            }
        }
        for (String link : links) {
            if (this.isAbort()) {
                logger.info("Decryption aborted...");
                return decryptedLinks;
            }
            Browser br2 = br.cloneBrowser();
            br2.getPage(link);
            // adware for some users!
            String[] redirectLinks = br2.getRegex("(\"|')(?![^\"']*optic4u\\.info)(/[^/\r\n\t]+/" + uid + "/[^\"\r\n\t]+)\\1").getColumn(1);
            if (redirectLinks == null || redirectLinks.length == 0) {
                // not redirects but final download link in html.
                String finallink = br2.getRegex("<a href=([^ ]+) TARGET='_blank'").getMatch(0);
                if (finallink == null) {
                    finallink = br2.getRegex("<div class=\"highlight redirecturl\">(.*?)</div>").getMatch(0);
                }
                if (finallink != null) {
                    final DownloadLink dl = createDownloadlink(finallink);
                    if (fp != null) {
                        fp.add(dl);
                    }
                    distribute(dl);
                    decryptedLinks.add(dl);
                    continue;
                }
                if ((redirectLinks == null || redirectLinks.length == 0) && finallink == null) {
                    logger.warning("Scanning for redirectLinks && finallinks failed, possible change in html, continuing...");
                    continue;
                }
            }
            logger.info("Found " + redirectLinks.length + " " + this.getHost() + " links to decrypt...");
            for (String singlelink : redirectLinks) {
                singlelink = singlelink.replace("\"", "").replace(" ", "");
                br2 = br.cloneBrowser();
                String dllink = null;
                // Handling for links that need to be regexed or that need to be get by redirect
                if (singlelink.matches("/[^/]+/.*?" + uid + ".*?/.*?")) {
                    br2.getPage(singlelink.trim());
                    dllink = br2.getRedirectLocation();
                    if (dllink == null) {
                        dllink = br2.getRegex("Please <a href=(\"|')?(http.*?)\\1 ").getMatch(1);
                        if (dllink == null) {
                            dllink = br2.getRegex("redirecturl\">(https?://[^<>]+)</div>").getMatch(0);
                        }
                    }
                } else {
                    // Handling for already regexed final-links
                    dllink = singlelink;
                }
                if (dllink == null || dllink.equals("")) {
                    // Continue away, randomised pages can cause failures.
                    logger.warning("Possible plugin error: " + param.toString());
                    logger.warning("Continuing...");
                    continue;
                }
                final DownloadLink fina = createDownloadlink(dllink);
                if (fp != null) {
                    fp.add(fina);
                }
                distribute(fina);
                decryptedLinks.add(fina);
            }
        }
        logger.info("Task Complete! : " + parameter);
        return decryptedLinks;
    }

    /* NO OVERRIDE!! */
    public boolean hasCaptcha(CryptedLink link, jd.plugins.Account acc) {
        return false;
    }

}