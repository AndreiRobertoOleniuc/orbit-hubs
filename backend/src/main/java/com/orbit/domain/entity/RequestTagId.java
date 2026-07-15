package com.orbit.domain.entity;

import java.io.Serializable;
import java.util.Objects;

/** Composite primary key for {@link RequestTag} — (request_id, tag_id). */
public class RequestTagId implements Serializable {

    private Long requestId;
    private Long tagId;

    public RequestTagId() { }

    public RequestTagId(Long requestId, Long tagId) {
        this.requestId = requestId;
        this.tagId = tagId;
    }

    public Long getRequestId() { return requestId; }
    public void setRequestId(Long requestId) { this.requestId = requestId; }
    public Long getTagId() { return tagId; }
    public void setTagId(Long tagId) { this.tagId = tagId; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof RequestTagId that)) return false;
        return Objects.equals(requestId, that.requestId) && Objects.equals(tagId, that.tagId);
    }

    @Override
    public int hashCode() { return Objects.hash(requestId, tagId); }
}