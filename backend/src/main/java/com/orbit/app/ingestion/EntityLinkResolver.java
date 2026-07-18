package com.orbit.app.ingestion;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.orbit.domain.entity.DiscoveryEntity;
import com.orbit.domain.entity.EntityLink;
import com.orbit.domain.repository.DiscoveryEntityRepository;
import com.orbit.domain.repository.EntityLinkRepository;

/**
 * Resolves the Person↔Organisation link (§10) — a "who works where" signal.
 * <p>
 * For a person with an {@code org_name}, it looks for an <em>existing</em>
 * organization entity whose name matches (normalized, legal-form-stripped) and,
 * if found, upserts a derived {@link EntityLink}. It <strong>never creates</strong>
 * an org entity: when nothing matches, no link is written and the person's plain
 * {@code org_name} stays as the unlinked workplace label. Derived links never
 * overwrite a {@code 'confirmed'} (user-set) link.
 */
@Service
public class EntityLinkResolver {

    private static final Logger log = LoggerFactory.getLogger(EntityLinkResolver.class);

    private final DiscoveryEntityRepository entities;
    private final EntityLinkRepository links;

    public EntityLinkResolver(DiscoveryEntityRepository entities, EntityLinkRepository links) {
        this.entities = entities;
        this.links = links;
    }

    /**
     * Reconcile a person's derived org link with their current {@code org_name}.
     * Adds a link when the name matches an existing org, removes a stale derived
     * link when the name changed to a non-match or a different org, and leaves a
     * {@code 'confirmed'} (user-set) link untouched. Safe to call repeatedly —
     * from enrichment and after a manual employer edit.
     */
    public void resolveForPerson(DiscoveryEntity person) {
        if (person == null || !"person".equalsIgnoreCase(person.getEntityType())) return;

        EntityLink existing = links.findFirstByPersonEntityId(person.getId()).orElse(null);
        if (existing != null && "confirmed".equals(existing.getSource())) {
            return; // user-confirmed link wins — never auto-changed
        }

        Long target = null;
        String orgName = person.getOrgName();
        if (orgName != null && !orgName.isBlank()) {
            String norm = normalize(orgName);
            if (!norm.isEmpty()) target = findMatchingOrg(norm, person.getId());
        }

        // Remove a stale derived link (org_name changed away from it)
        if (existing != null && (target == null || !target.equals(existing.getOrgEntityId()))) {
            links.delete(existing);
            links.flush();
            log.info("Unlinked person #{} from org #{} (org_name=\"{}\")",
                    person.getId(), existing.getOrgEntityId(), orgName);
            existing = null;
        }

        // Add the link when the current name matches an existing org
        if (target != null && existing == null) {
            links.save(new EntityLink(person.getId(), target, null, "derived"));
            log.info("Linked person #{} → org #{} (derived, org_name=\"{}\")",
                    person.getId(), target, orgName);
        }
    }

    /** First organization entity whose normalized name matches; null if none. */
    private Long findMatchingOrg(String target, Long selfId) {
        List<DiscoveryEntity> orgs = entities.findByEntityType("organization");
        for (DiscoveryEntity org : orgs) {
            if (org.getId().equals(selfId)) continue;
            if (matches(target, normalize(org.getDisplayName()))
                    || matches(target, normalize(org.getOrgName()))) {
                return org.getId();
            }
        }
        return null;
    }

    /**
     * Conservative match: exact after normalization, or one name fully contains
     * the other as a token boundary (e.g. "soobr" ⊂ "soobr smart cleaning").
     * Kept tight to avoid false links — a wrong link is worse than none.
     */
    private static boolean matches(String a, String b) {
        if (a.isEmpty() || b.isEmpty()) return false;
        if (a.equals(b)) return true;
        return containsAsToken(a, b) || containsAsToken(b, a);
    }

    private static boolean containsAsToken(String haystack, String needle) {
        if (needle.length() < 4) return false; // too short → unsafe
        return (" " + haystack + " ").contains(" " + needle + " ");
    }

    /** Lowercase, strip legal forms + punctuation, collapse whitespace. */
    private static String normalize(String name) {
        if (name == null) return "";
        String n = name.toLowerCase()
                .replaceAll("[^a-z0-9äöüéèàç\\s]", " ")
                .replaceAll("\\b(ag|gmbh|sa|sarl|ltd|inc|llc|plc|co|kg|se)\\b", " ")
                .replaceAll("\\s+", " ")
                .trim();
        return n;
    }
}
