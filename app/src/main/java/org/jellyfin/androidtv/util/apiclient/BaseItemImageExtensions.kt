package org.jellyfin.androidtv.util.apiclient

import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.ImageType

/**
 * Extension functions for BaseItemDto to simplify common image access patterns.
 * These functions reduce boilerplate when accessing item images with fallbacks.
 */

/**
 * Get the primary image for this item.
 * @return The primary image, or null if not available
 */
fun BaseItemDto.getPrimaryImage() = itemImages[ImageType.PRIMARY]

/**
 * Get the logo image for this item, with fallback to parent logo.
 * @return The logo image from the item or its parent, or null if neither exists
 */
fun BaseItemDto.getLogoImage() = itemImages[ImageType.LOGO] ?: parentImages[ImageType.LOGO]

/**
 * Get the thumb image for this item.
 * @return The thumb image, or null if not available
 */
fun BaseItemDto.getThumbImage() = itemImages[ImageType.THUMB]

/**
 * Get the backdrop image for this item.
 * @return The backdrop image, or null if not available
 */
fun BaseItemDto.getBackdropImage() = itemImages[ImageType.BACKDROP]

/**
 * Get the banner image for this item.
 * @return The banner image, or null if not available
 */
fun BaseItemDto.getBannerImage() = itemImages[ImageType.BANNER]

/**
 * Get the primary image with fallback to parent's primary image.
 * Useful for episodes that might use series artwork.
 * @return The primary image from the item or its parent, or null if neither exists
 */
fun BaseItemDto.getPrimaryImageWithFallback() = 
	itemImages[ImageType.PRIMARY] ?: parentImages[ImageType.PRIMARY]

/**
 * Get the thumb image with fallback to parent's thumb image.
 * Useful for episodes that might use series thumbnails.
 * @return The thumb image from the item or its parent, or null if neither exists
 */
fun BaseItemDto.getThumbImageWithFallback() = 
	itemImages[ImageType.THUMB] ?: parentImages[ImageType.THUMB]
