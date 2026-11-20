package com.ceybertech.pdfmedemo.audit;

import java.util.UUID;

import org.springframework.stereotype.Component;

/**
 * The one place that answers "who is doing this?". Every created_by, updated_by, published_by,
 * deleted_by and owner_user_id comes from here.
 * <p>
 * There is no login yet, so it is always the system user. When the identity service exists,
 * read the user's id from the login token here; nothing else needs to change.
 */
@Component
public class CurrentUser {

	/** Stored for every change made before login exists. The frontend shows it as "System". */
	public static final UUID SYSTEM_USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000000");

	public UUID id() {
		return SYSTEM_USER_ID;
	}

}
