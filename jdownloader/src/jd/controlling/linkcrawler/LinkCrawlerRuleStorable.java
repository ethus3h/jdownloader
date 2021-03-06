package jd.controlling.linkcrawler;

import org.appwork.storage.JSonStorage;
import org.appwork.storage.Storable;

public class LinkCrawlerRuleStorable extends LinkCrawlerRule implements Storable {

    public static void main(String[] args) {
        System.out.println(JSonStorage.toString(new LinkCrawlerRuleStorable()));
    }

    public LinkCrawlerRuleStorable(/* Storable */) {
        super();
    }

    public LinkCrawlerRuleStorable(LinkCrawlerRule rule) {
        super(rule.getId());
        this.setEnabled(rule.isEnabled());
        this.setName(rule.getName());
        this.setPattern(rule.getPattern());
        this.setRule(rule.getRule());
        this.setMaxDecryptDepth(rule.getMaxDecryptDepth());
        this.setPackageNamePattern(rule.getPackageNamePattern());
        this.setFormPattern(rule.getFormPattern());
        this.setDeepPattern(rule.getDeepPattern());
        this.setRewriteReplaceWith(rule.getRewriteReplaceWith());
    }

    public void setId(long id) {
        this.id.setID(id);
    }

    public LinkCrawlerRule _getLinkCrawlerRule() {
        final LinkCrawlerRule ret = new LinkCrawlerRule(this.getId());
        ret.setMaxDecryptDepth(getMaxDecryptDepth());
        ret.setEnabled(isEnabled());
        ret.setName(getName());
        ret.setPattern(getPattern());
        ret.setRule(getRule());
        ret.setPackageNamePattern(getPackageNamePattern());
        ret.setFormPattern(getFormPattern());
        ret.setDeepPattern(getDeepPattern());
        ret.setRewriteReplaceWith(getRewriteReplaceWith());
        return ret;
    }

}
