/*
 * Copyright (C) 2026 The Sprout contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package dev.sprout.core.model

import java.util.UUID

/**
 * Row ids are UUID strings rather than autoincrementing integers.
 *
 * This is a sync decision made early on purpose: two devices editing offline must be able to
 * create rows that never collide, without a server handing out ids. See docs/02-app-design.md,
 * "Backup & sync".
 */
public fun newId(): String = UUID.randomUUID().toString()
