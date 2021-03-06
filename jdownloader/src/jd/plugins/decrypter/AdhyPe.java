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

import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;

import org.jdownloader.captcha.v2.challenge.recaptcha.v2.CaptchaHelperCrawlerPluginRecaptchaV2;
import org.jdownloader.scripting.JavaScriptEngineFactory;

import jd.PluginWrapper;
import jd.controlling.ProgressController;
import jd.http.Browser;
import jd.http.Request;
import jd.nutils.encoding.HTMLEntities;
import jd.parser.Regex;
import jd.plugins.CryptedLink;
import jd.plugins.DecrypterException;
import jd.plugins.DecrypterPlugin;
import jd.plugins.DownloadLink;
import jd.plugins.PluginForDecrypt;
import jd.plugins.components.PluginJSonUtils;

/**
 *
 * @author psp
 * @author raztoki
 *
 */
@DecrypterPlugin(revision = "$Revision: 35523 $", interfaceVersion = 3, names = { "ah.pe" }, urls = { "https?://(?:www\\.)?ah\\.pe/([A-Za-z0-9]+)" })
public class AdhyPe extends PluginForDecrypt {

    public AdhyPe(PluginWrapper wrapper) {
        super(wrapper);
    }

    @Override
    public int getMaxConcurrentProcessingInstances() {
        return 1;
    }

    public ArrayList<DownloadLink> decryptIt(CryptedLink param, ProgressController progress) throws Exception {
        final ArrayList<DownloadLink> decryptedLinks = new ArrayList<DownloadLink>();
        final String parameter = param.toString();
        final int repeat = 4;
        for (int i = 0; i <= repeat; i++) {
            br = new Browser();
            br.setFollowRedirects(false);
            br.getPage(parameter);
            if (br.getHttpConnection().getResponseCode() == 404) {
                decryptedLinks.add(createOfflinelink(parameter));
                return decryptedLinks;
            }
            final String fuid = new Regex(parameter, this.getSupportedLinks()).getMatch(0);
            final String unwise = unWise();
            final String key = getKey(unwise, fuid);
            if (key == null || unwise == null) {
                throw new DecrypterException(DecrypterException.PLUGIN_DEFECT);
            }
            Browser br = this.br.cloneBrowser();
            sleep(7500, param);
            // only required for this request
            br.getHeaders().put("Accept", "*/*");
            br.getHeaders().put("X-Requested-With", "XMLHttpRequest");
            br.getPage(Request.getLocation(key, br.getRequest()));
            String redirecturl = br.toString();
            // recaptchav2
            if (isCaptcha(unwise)) {
                if ((i + 1) <= (repeat - 1)) {
                    sleep(30000L, param);
                    continue;
                }
                final String recaptchaV2Response = new CaptchaHelperCrawlerPluginRecaptchaV2(this, this.br).getToken();
                if (recaptchaV2Response != null) {
                    redirecturl += "." + recaptchaV2Response;
                }
            }
            br = this.br.cloneBrowser();
            br.getPage(Request.getLocation(redirecturl, br.getRequest()));
            final String finallink = HTMLEntities.unhtmlentities(br.getRedirectLocation());
            if (finallink == null) {
                logger.warning("Decrypter broken for link: " + parameter);
                return null;
            }
            decryptedLinks.add(createDownloadlink(finallink));
            return decryptedLinks;
        }
        return decryptedLinks;

    }

    private boolean isCaptcha(String unwise) {
        // its evaulated by js, see how long this will work for
        final String captchaKey = new Regex(unwise, "var\\s+[a-zA-Z0-9]+\\s*=\\s*(0|1)\\s*;\\s*var\\s+password\\s*=\\s*(?:0|1);").getMatch(0);
        return PluginJSonUtils.parseBoolean(captchaKey);
    }

    private String unWise() {
        String result = null;
        String fn = br.getRegex("eval\\((function\\(.*?\'\\))\\);").getMatch(0);
        if (fn == null) {
            return null;
        }
        final ScriptEngineManager manager = JavaScriptEngineFactory.getScriptEngineManager(this);
        final ScriptEngine engine = manager.getEngineByName("javascript");
        try {
            engine.eval("var res = " + fn);
            result = (String) engine.get("res");
            result = new Regex(result, "eval\\((.*?)\\);$").getMatch(0);
            engine.eval("res = " + result);
            result = (String) engine.get("res");
            String res[] = result.split(";\\s;");
            engine.eval("res = " + new Regex(res[res.length - 1], "eval\\((.*?)\\);$").getMatch(0));
            result = (String) engine.get("res");
        } catch (final Exception e) {
            logger.log(e);
            return null;
        }
        return result;
    }

    private String getKey(final String input, final String fuid) {
        if (input == null) {
            return input;
        }
        final String key = new Regex(input, "var\\s+[a-zA-Z0-9]+\\s*=\\s*('|\"|)(g/[a-f0-9]+\\." + fuid + "\\.[a-f0-9]+)\\1").getMatch(1);
        return key;
    }

}
