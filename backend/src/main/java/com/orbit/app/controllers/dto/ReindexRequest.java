package com.orbit.app.controllers.dto;

import java.util.List;

/**
 * Which profiles to re-evaluate.
 *
 * @param entityIds the entities to queue; {@code null} or empty queues every
 *                  entity that has a source
 */
public record ReindexRequest(List<Long> entityIds) {
}
