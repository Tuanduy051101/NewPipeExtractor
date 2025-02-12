/*
 * Created by Christian Schabesberger on 06.08.15.
 *
 * Copyright (C) 2019 Christian Schabesberger <chris.schabesberger@mailbox.org>
 * YoutubeStreamExtractor.java is part of NewPipe Extractor.
 *
 * NewPipe Extractor is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * NewPipe Extractor is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with NewPipe Extractor. If not, see <https://www.gnu.org/licenses/>.
 */

package org.schabi.newpipe.extractor.services.youtube.extractors;

import static org.schabi.newpipe.extractor.services.youtube.ItagItem.APPROX_DURATION_MS_UNKNOWN;
import static org.schabi.newpipe.extractor.services.youtube.ItagItem.CONTENT_LENGTH_UNKNOWN;
import static org.schabi.newpipe.extractor.services.youtube.YoutubeDescriptionHelper.attributedDescriptionToHtml;
import static org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper.CONTENT_CHECK_OK;
import static org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper.CPN;
import static org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper.RACY_CHECK_OK;
import static org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper.VIDEO_ID;
import static org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper.fixThumbnailUrl;
import static org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper.generateContentPlaybackNonce;
import static org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper.getImagesFromThumbnailsArray;
import static org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper.getJsonPostResponse;
import static org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper.getTextFromObject;
import static org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper.prepareDesktopJsonBuilder;
import static org.schabi.newpipe.extractor.utils.Utils.isNullOrEmpty;

import com.grack.nanojson.JsonArray;
import com.grack.nanojson.JsonObject;
import com.grack.nanojson.JsonWriter;

import org.schabi.newpipe.extractor.Image;
import org.schabi.newpipe.extractor.MediaFormat;
import org.schabi.newpipe.extractor.MetaInfo;
import org.schabi.newpipe.extractor.MultiInfoItemsCollector;
import org.schabi.newpipe.extractor.StreamingService;
import org.schabi.newpipe.extractor.downloader.Downloader;
import org.schabi.newpipe.extractor.exceptions.AgeRestrictedContentException;
import org.schabi.newpipe.extractor.exceptions.ContentNotAvailableException;
import org.schabi.newpipe.extractor.exceptions.ExtractionException;
import org.schabi.newpipe.extractor.exceptions.GeographicRestrictionException;
import org.schabi.newpipe.extractor.exceptions.PaidContentException;
import org.schabi.newpipe.extractor.exceptions.ParsingException;
import org.schabi.newpipe.extractor.exceptions.PrivateContentException;
import org.schabi.newpipe.extractor.exceptions.YoutubeMusicPremiumContentException;
import org.schabi.newpipe.extractor.linkhandler.LinkHandler;
import org.schabi.newpipe.extractor.localization.ContentCountry;
import org.schabi.newpipe.extractor.localization.DateWrapper;
import org.schabi.newpipe.extractor.localization.Localization;
import org.schabi.newpipe.extractor.localization.TimeAgoParser;
import org.schabi.newpipe.extractor.localization.TimeAgoPatternsManager;
import org.schabi.newpipe.extractor.services.youtube.ItagItem;
import org.schabi.newpipe.extractor.services.youtube.PoTokenProvider;
import org.schabi.newpipe.extractor.services.youtube.PoTokenResult;
import org.schabi.newpipe.extractor.services.youtube.YoutubeJavaScriptPlayerManager;
import org.schabi.newpipe.extractor.services.youtube.YoutubeMetaInfoHelper;
import org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper;
import org.schabi.newpipe.extractor.services.youtube.YoutubeStreamHelper;
import org.schabi.newpipe.extractor.services.youtube.linkHandler.YoutubeChannelLinkHandlerFactory;
import org.schabi.newpipe.extractor.stream.AudioStream;
import org.schabi.newpipe.extractor.stream.DeliveryMethod;
import org.schabi.newpipe.extractor.stream.Description;
import org.schabi.newpipe.extractor.stream.Frameset;
import org.schabi.newpipe.extractor.stream.Stream;
import org.schabi.newpipe.extractor.stream.StreamExtractor;
import org.schabi.newpipe.extractor.stream.StreamSegment;
import org.schabi.newpipe.extractor.stream.StreamType;
import org.schabi.newpipe.extractor.stream.SubtitlesStream;
import org.schabi.newpipe.extractor.stream.VideoStream;
import org.schabi.newpipe.extractor.utils.JsonUtils;
import org.schabi.newpipe.extractor.utils.LocaleCompat;
import org.schabi.newpipe.extractor.utils.Pair;
import org.schabi.newpipe.extractor.utils.Parser;
import org.schabi.newpipe.extractor.utils.Utils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
//import java.util.HashMap;
import java.util.List;
import java.util.Locale;
//import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

// ttd-edit-v1
//import com.google.common.cache.Cache;
//import com.google.common.cache.CacheBuilder;
import java.util.concurrent.CompletableFuture;
//import java.util.concurrent.ExecutorService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class YoutubeStreamExtractor extends StreamExtractor {
    private static final Logger LOG = LoggerFactory.getLogger(YoutubeStreamExtractor.class);
    @Nullable
    private static PoTokenProvider poTokenProvider;
    private static boolean fetchIosClient;

    private JsonObject playerResponse;
    private JsonObject nextResponse;

    @Nullable
    private JsonObject iosStreamingData;
    @Nullable
    private JsonObject androidStreamingData;
    @Nullable
    private JsonObject html5StreamingData;

    private JsonObject videoPrimaryInfoRenderer;
    private JsonObject videoSecondaryInfoRenderer;
    private JsonObject playerMicroFormatRenderer;
    private JsonObject playerCaptionsTracklistRenderer;
    private int ageLimit = -1;
    private StreamType streamType;

    // We need to store the contentPlaybackNonces because we need to append them to videoplayback
    // URLs (with the cpn parameter).
    // Also because a nonce should be unique, it should be different between clients used, so
    // three different strings are used.
    private String iosCpn;
    private String androidCpn;
    private String html5Cpn;

    @Nullable
    private String html5StreamingUrlsPoToken;
    @Nullable
    private String androidStreamingUrlsPoToken;
    @Nullable
    private String iosStreamingUrlsPoToken;



    public YoutubeStreamExtractor(final StreamingService service, final LinkHandler linkHandler) {
        super(service, linkHandler);
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Impl
    //////////////////////////////////////////////////////////////////////////*/

    @Nonnull
    @Override
    public String getName() throws ParsingException {
        assertPageFetched();
        String title;

        // Try to get the video's original title, which is untranslated
        title = playerResponse.getObject("videoDetails").getString("title");

        if (isNullOrEmpty(title)) {
            title = getTextFromObject(getVideoPrimaryInfoRenderer().getObject("title"));

            if (isNullOrEmpty(title)) {
                throw new ParsingException("Could not get name");
            }
        }

        return title;
    }

    @Nullable
    @Override
    public String getTextualUploadDate() throws ParsingException {
        if (!playerMicroFormatRenderer.getString("uploadDate", "").isEmpty()) {
            return playerMicroFormatRenderer.getString("uploadDate");
        } else if (!playerMicroFormatRenderer.getString("publishDate", "").isEmpty()) {
            return playerMicroFormatRenderer.getString("publishDate");
        }

        final JsonObject liveDetails = playerMicroFormatRenderer.getObject(
                "liveBroadcastDetails");
        if (!liveDetails.getString("endTimestamp", "").isEmpty()) {
            // an ended live stream
            return liveDetails.getString("endTimestamp");
        } else if (!liveDetails.getString("startTimestamp", "").isEmpty()) {
            // a running live stream
            return liveDetails.getString("startTimestamp");
        } else if (getStreamType() == StreamType.LIVE_STREAM) {
            // this should never be reached, but a live stream without upload date is valid
            return null;
        }

        final String videoPrimaryInfoRendererDateText =
                getTextFromObject(getVideoPrimaryInfoRenderer().getObject("dateText"));

        if (videoPrimaryInfoRendererDateText != null) {
            if (videoPrimaryInfoRendererDateText.startsWith("Premiered")) {
                final String time = videoPrimaryInfoRendererDateText.substring(13);

                try { // Premiered 20 hours ago
                    final TimeAgoParser timeAgoParser = TimeAgoPatternsManager.getTimeAgoParserFor(
                            new Localization("en"));
                    final OffsetDateTime parsedTime = timeAgoParser.parse(time).offsetDateTime();
                    return DateTimeFormatter.ISO_LOCAL_DATE.format(parsedTime);
                } catch (final Exception ignored) {
                }

                try { // Premiered Feb 21, 2020
                    final LocalDate localDate = LocalDate.parse(time,
                            DateTimeFormatter.ofPattern("MMM dd, yyyy", Locale.ENGLISH));
                    return DateTimeFormatter.ISO_LOCAL_DATE.format(localDate);
                } catch (final Exception ignored) {
                }

                try { // Premiered on 21 Feb 2020
                    final LocalDate localDate = LocalDate.parse(time,
                            DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH));
                    return DateTimeFormatter.ISO_LOCAL_DATE.format(localDate);
                } catch (final Exception ignored) {
                }
            }

            try {
                // TODO: this parses English formatted dates only, we need a better approach to
                //  parse the textual date
                final LocalDate localDate = LocalDate.parse(videoPrimaryInfoRendererDateText,
                        DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH));
                return DateTimeFormatter.ISO_LOCAL_DATE.format(localDate);
            } catch (final Exception e) {
                throw new ParsingException("Could not get upload date", e);
            }
        }

        throw new ParsingException("Could not get upload date");
    }

    @Override
    public DateWrapper getUploadDate() throws ParsingException {
        final String textualUploadDate = getTextualUploadDate();

        if (isNullOrEmpty(textualUploadDate)) {
            return null;
        }

        return new DateWrapper(YoutubeParsingHelper.parseDateFrom(textualUploadDate), true);
    }

    @Nonnull
    @Override
    public List<Image> getThumbnails() throws ParsingException {
        assertPageFetched();
        try {
            return getImagesFromThumbnailsArray(playerResponse.getObject("videoDetails")
                    .getObject("thumbnail")
                    .getArray("thumbnails"));
        } catch (final Exception e) {
            throw new ParsingException("Could not get thumbnails");
        }
    }

    @Nonnull
    @Override
    public Description getDescription() throws ParsingException {
        assertPageFetched();
        // Description with more info on links
        final String videoSecondaryInfoRendererDescription = getTextFromObject(
                getVideoSecondaryInfoRenderer().getObject("description"),
                true);
        if (!isNullOrEmpty(videoSecondaryInfoRendererDescription)) {
            return new Description(videoSecondaryInfoRendererDescription, Description.HTML);
        }

        final String attributedDescription = attributedDescriptionToHtml(
                getVideoSecondaryInfoRenderer().getObject("attributedDescription"));
        if (!isNullOrEmpty(attributedDescription)) {
            return new Description(attributedDescription, Description.HTML);
        }

        String description = playerResponse.getObject("videoDetails")
                .getString("shortDescription");
        if (description == null) {
            final JsonObject descriptionObject = playerMicroFormatRenderer.getObject("description");
            description = getTextFromObject(descriptionObject);
        }

        // Raw non-html description
        return new Description(description, Description.PLAIN_TEXT);
    }

    @Override
    public int getAgeLimit() throws ParsingException {
        if (ageLimit != -1) {
            return ageLimit;
        }

        final boolean ageRestricted = getVideoSecondaryInfoRenderer()
                .getObject("metadataRowContainer")
                .getObject("metadataRowContainerRenderer")
                .getArray("rows")
                .stream()
                // Only JsonObjects allowed
                .filter(JsonObject.class::isInstance)
                .map(JsonObject.class::cast)
                .flatMap(metadataRow -> metadataRow
                        .getObject("metadataRowRenderer")
                        .getArray("contents")
                        .stream()
                        // Only JsonObjects allowed
                        .filter(JsonObject.class::isInstance)
                        .map(JsonObject.class::cast))
                .flatMap(content -> content
                        .getArray("runs")
                        .stream()
                        // Only JsonObjects allowed
                        .filter(JsonObject.class::isInstance)
                        .map(JsonObject.class::cast))
                .map(run -> run.getString("text", ""))
                .anyMatch(rowText -> rowText.contains("Age-restricted"));

        ageLimit = ageRestricted ? 18 : NO_AGE_LIMIT;
        return ageLimit;
    }

    @Override
    public long getLength() throws ParsingException {
        assertPageFetched();

        try {
            final String duration = playerResponse
                    .getObject("videoDetails")
                    .getString("lengthSeconds");
            return Long.parseLong(duration);
        } catch (final Exception e) {
            return getDurationFromFirstAdaptiveFormat(Arrays.asList(
                    html5StreamingData, androidStreamingData, iosStreamingData));
        }
    }

    private int getDurationFromFirstAdaptiveFormat(@Nonnull final List<JsonObject> streamingDatas)
            throws ParsingException {
        for (final JsonObject streamingData : streamingDatas) {
            final JsonArray adaptiveFormats = streamingData.getArray(ADAPTIVE_FORMATS);
            if (adaptiveFormats.isEmpty()) {
                continue;
            }

            final String durationMs = adaptiveFormats.getObject(0)
                    .getString("approxDurationMs");
            try {
                return Math.round(Long.parseLong(durationMs) / 1000f);
            } catch (final NumberFormatException ignored) {
            }
        }

        throw new ParsingException("Could not get duration");
    }

    /**
     * Attempts to parse (and return) the offset to start playing the video from.
     *
     * @return the offset (in seconds), or 0 if no timestamp is found.
     */
    @Override
    public long getTimeStamp() throws ParsingException {
        final long timestamp =
                getTimestampSeconds("((#|&|\\?)t=\\d*h?\\d*m?\\d+s?)");

        if (timestamp == -2) {
            // Regex for timestamp was not found
            return 0;
        }
        return timestamp;
    }

    @Override
    public long getViewCount() throws ParsingException {
        String views = getTextFromObject(getVideoPrimaryInfoRenderer().getObject("viewCount")
                .getObject("videoViewCountRenderer").getObject("viewCount"));

        if (isNullOrEmpty(views)) {
            views = playerResponse.getObject("videoDetails").getString("viewCount");

            if (isNullOrEmpty(views)) {
                throw new ParsingException("Could not get view count");
            }
        }

        if (views.toLowerCase().contains("no views")) {
            return 0;
        }

        return Long.parseLong(Utils.removeNonDigitCharacters(views));
    }

    @Override
    public long getLikeCount() throws ParsingException {
        assertPageFetched();

        // If ratings are not allowed, there is no like count available
        if (!playerResponse.getObject("videoDetails").getBoolean("allowRatings")) {
            return -1L;
        }

        final JsonArray topLevelButtons = getVideoPrimaryInfoRenderer()
                .getObject("videoActions")
                .getObject("menuRenderer")
                .getArray("topLevelButtons");

        try {
            return parseLikeCountFromLikeButtonViewModel(topLevelButtons);
        } catch (final ParsingException ignored) {
            // A segmentedLikeDislikeButtonRenderer could be returned instead of a
            // segmentedLikeDislikeButtonViewModel, so ignore extraction errors relative to
            // segmentedLikeDislikeButtonViewModel object
        }

        try {
            return parseLikeCountFromLikeButtonRenderer(topLevelButtons);
        } catch (final ParsingException e) {
            throw new ParsingException("Could not get like count", e);
        }
    }

    private static long parseLikeCountFromLikeButtonRenderer(
            @Nonnull final JsonArray topLevelButtons) throws ParsingException {
        String likesString = null;
        final JsonObject likeToggleButtonRenderer = topLevelButtons.stream()
                .filter(JsonObject.class::isInstance)
                .map(JsonObject.class::cast)
                .map(button -> button.getObject("segmentedLikeDislikeButtonRenderer")
                        .getObject("likeButton")
                        .getObject("toggleButtonRenderer"))
                .filter(toggleButtonRenderer -> !isNullOrEmpty(toggleButtonRenderer))
                .findFirst()
                .orElse(null);

        if (likeToggleButtonRenderer != null) {
            // Use one of the accessibility strings available (this one has the same path as the
            // one used for comments' like count extraction)
            likesString = likeToggleButtonRenderer.getObject("accessibilityData")
                    .getObject("accessibilityData")
                    .getString("label");

            // Use the other accessibility string available which contains the exact like count
            if (likesString == null) {
                likesString = likeToggleButtonRenderer.getObject("accessibility")
                        .getString("label");
            }

            // Last method: use the defaultText's accessibility data, which contains the exact like
            // count too, except when it is equal to 0, where a localized string is returned instead
            if (likesString == null) {
                likesString = likeToggleButtonRenderer.getObject("defaultText")
                        .getObject("accessibility")
                        .getObject("accessibilityData")
                        .getString("label");
            }

            // This check only works with English localizations!
            if (likesString != null && likesString.toLowerCase().contains("no likes")) {
                return 0;
            }
        }

        // If ratings are allowed and the likes string is null, it means that we couldn't extract
        // the full like count from accessibility data
        if (likesString == null) {
            throw new ParsingException("Could not get like count from accessibility data");
        }

        try {
            return Long.parseLong(Utils.removeNonDigitCharacters(likesString));
        } catch (final NumberFormatException e) {
            throw new ParsingException("Could not parse \"" + likesString + "\" as a long", e);
        }
    }

    private static long parseLikeCountFromLikeButtonViewModel(
            @Nonnull final JsonArray topLevelButtons) throws ParsingException {
        // Try first with the current video actions buttons data structure
        final JsonObject likeToggleButtonViewModel = topLevelButtons.stream()
                .filter(JsonObject.class::isInstance)
                .map(JsonObject.class::cast)
                .map(button -> button.getObject("segmentedLikeDislikeButtonViewModel")
                        .getObject("likeButtonViewModel")
                        .getObject("likeButtonViewModel")
                        .getObject("toggleButtonViewModel")
                        .getObject("toggleButtonViewModel")
                        .getObject("defaultButtonViewModel")
                        .getObject("buttonViewModel"))
                .filter(buttonViewModel -> !isNullOrEmpty(buttonViewModel))
                .findFirst()
                .orElse(null);

        if (likeToggleButtonViewModel == null) {
            throw new ParsingException("Could not find buttonViewModel object");
        }

        final String accessibilityText = likeToggleButtonViewModel.getString("accessibilityText");
        if (accessibilityText == null) {
            throw new ParsingException("Could not find buttonViewModel's accessibilityText string");
        }

        // The like count is always returned as a number in this element, even for videos with no
        // likes
        try {
            return Long.parseLong(Utils.removeNonDigitCharacters(accessibilityText));
        } catch (final NumberFormatException e) {
            throw new ParsingException(
                    "Could not parse \"" + accessibilityText + "\" as a long", e);
        }
    }

    @Nonnull
    @Override
    public String getUploaderUrl() throws ParsingException {
        assertPageFetched();

        // Don't use the id in the videoSecondaryRenderer object to get real id of the uploader
        // The difference between the real id of the channel and the displayed id is especially
        // visible for music channels and autogenerated channels.
        final String uploaderId = playerResponse.getObject("videoDetails").getString("channelId");
        if (!isNullOrEmpty(uploaderId)) {
            return YoutubeChannelLinkHandlerFactory.getInstance().getUrl("channel/" + uploaderId);
        }

        throw new ParsingException("Could not get uploader url");
    }

    @Nonnull
    @Override
    public String getUploaderName() throws ParsingException {
        assertPageFetched();

        // Don't use the name in the videoSecondaryRenderer object to get real name of the uploader
        // The difference between the real name of the channel and the displayed name is especially
        // visible for music channels and autogenerated channels.
        final String uploaderName = playerResponse.getObject("videoDetails").getString("author");
        if (isNullOrEmpty(uploaderName)) {
            throw new ParsingException("Could not get uploader name");
        }

        return uploaderName;
    }

    @Override
    public boolean isUploaderVerified() throws ParsingException {
        return YoutubeParsingHelper.isVerified(
                getVideoSecondaryInfoRenderer()
                        .getObject("owner")
                        .getObject("videoOwnerRenderer")
                        .getArray("badges"));
    }

    @Nonnull
    @Override
    public List<Image> getUploaderAvatars() throws ParsingException {
        assertPageFetched();

        final List<Image> imageList = getImagesFromThumbnailsArray(
                getVideoSecondaryInfoRenderer().getObject("owner")
                        .getObject("videoOwnerRenderer")
                        .getObject("thumbnail")
                        .getArray("thumbnails"));

        if (imageList.isEmpty() && ageLimit == NO_AGE_LIMIT) {
            throw new ParsingException("Could not get uploader avatars");
        }

        return imageList;
    }

    @Override
    public long getUploaderSubscriberCount() throws ParsingException {
        final JsonObject videoOwnerRenderer = JsonUtils.getObject(videoSecondaryInfoRenderer,
                "owner.videoOwnerRenderer");
        if (!videoOwnerRenderer.has("subscriberCountText")) {
            return UNKNOWN_SUBSCRIBER_COUNT;
        }
        try {
            return Utils.mixedNumberWordToLong(getTextFromObject(videoOwnerRenderer
                    .getObject("subscriberCountText")));
        } catch (final NumberFormatException e) {
            throw new ParsingException("Could not get uploader subscriber count", e);
        }
    }

    @Nonnull
    @Override
    public String getDashMpdUrl() throws ParsingException {
        assertPageFetched();

        // There is no DASH manifest available with the iOS client
        return getManifestUrl(
                "dash",
                Arrays.asList(
                        new Pair<>(androidStreamingData, androidStreamingUrlsPoToken),
                        new Pair<>(html5StreamingData, html5StreamingUrlsPoToken)),
                // Return version 7 of the DASH manifest, which is the latest one, reducing
                // manifest size and allowing playback with some DASH players
                "mpd_version=7");
    }

    @Nonnull
    @Override
    public String getHlsUrl() throws ParsingException {
        assertPageFetched();

        // Return HLS manifest of the iOS client first because on livestreams, the HLS manifest
        // returned has separated audio and video streams and poTokens requirement do not seem to
        // impact HLS formats (if a poToken is provided, it is added)
        // Also, on videos, non-iOS clients don't have an HLS manifest URL in their player response
        // unless a Safari macOS user agent is used
        return getManifestUrl(
                "hls",
                Arrays.asList(
                        new Pair<>(iosStreamingData, iosStreamingUrlsPoToken),
                        new Pair<>(androidStreamingData, androidStreamingUrlsPoToken),
                        new Pair<>(html5StreamingData, html5StreamingUrlsPoToken)),
                "");
    }

    @Nonnull
    private static String getManifestUrl(
            @Nonnull final String manifestType,
            @Nonnull final List<Pair<JsonObject, String>> streamingDataObjects,
            @Nonnull final String partToAppendToManifestUrlEnd) {
        final String manifestKey = manifestType + "ManifestUrl";

        for (final Pair<JsonObject, String> streamingDataObj : streamingDataObjects) {
            if (streamingDataObj.getFirst() != null) {
                final String manifestUrl = streamingDataObj.getFirst().getString(manifestKey);
                if (isNullOrEmpty(manifestUrl)) {
                    continue;
                }

                // If poToken is not null, add it to manifest URL
                if (streamingDataObj.getSecond() == null) {
                    return manifestUrl + "?" + partToAppendToManifestUrlEnd;
                } else {
                    return manifestUrl + "?pot=" + streamingDataObj.getSecond() + "&"
                            + partToAppendToManifestUrlEnd;
                }
            }
        }

        return "";
    }

    @Override
    public List<AudioStream> getAudioStreams() throws ExtractionException {
        assertPageFetched();
        return getItags(ADAPTIVE_FORMATS, ItagItem.ItagType.AUDIO,
                getAudioStreamBuilderHelper(), "audio");
    }

    @Override
    public List<VideoStream> getVideoStreams() throws ExtractionException {
        assertPageFetched();
        return getItags(FORMATS, ItagItem.ItagType.VIDEO,
                getVideoStreamBuilderHelper(false), "video");
    }

    @Override
    public List<VideoStream> getVideoOnlyStreams() throws ExtractionException {
        assertPageFetched();
        return getItags(ADAPTIVE_FORMATS, ItagItem.ItagType.VIDEO_ONLY,
                getVideoStreamBuilderHelper(true), "video-only");
    }

    @Override
    @Nonnull
    public List<SubtitlesStream> getSubtitlesDefault() throws ParsingException {
        return getSubtitles(MediaFormat.TTML);
    }

    @Override
    @Nonnull
    public List<SubtitlesStream> getSubtitles(final MediaFormat format) throws ParsingException {
        assertPageFetched();

        // We cannot store the subtitles list because the media format may change
        final List<SubtitlesStream> subtitlesToReturn = new ArrayList<>();
        final JsonArray captionsArray = playerCaptionsTracklistRenderer.getArray("captionTracks");
        // TODO: use this to apply auto translation to different language from a source language
        // final JsonArray autoCaptionsArray = renderer.getArray("translationLanguages");

        for (int i = 0; i < captionsArray.size(); i++) {
            final String languageCode = captionsArray.getObject(i).getString("languageCode");
            final String baseUrl = captionsArray.getObject(i).getString("baseUrl");
            final String vssId = captionsArray.getObject(i).getString("vssId");

            if (languageCode != null && baseUrl != null && vssId != null) {
                final boolean isAutoGenerated = vssId.startsWith("a.");
                final String cleanUrl = baseUrl
                        // Remove preexisting format if exists
                        .replaceAll("&fmt=[^&]*", "")
                        // Remove translation language
                        .replaceAll("&tlang=[^&]*", "");

                subtitlesToReturn.add(new SubtitlesStream.Builder()
                        .setContent(cleanUrl + "&fmt=" + format.getSuffix(), true)
                        .setMediaFormat(format)
                        .setLanguageCode(languageCode)
                        .setAutoGenerated(isAutoGenerated)
                        .build());
            }
        }

        return subtitlesToReturn;
    }

    @Override
    public StreamType getStreamType() {
        assertPageFetched();

        return streamType;
    }

    private void setStreamType() {
        if (playerResponse.getObject(PLAYABILITY_STATUS).has("liveStreamability")) {
            streamType = StreamType.LIVE_STREAM;
        } else if (playerResponse.getObject("videoDetails").getBoolean("isPostLiveDvr", false)) {
            streamType = StreamType.POST_LIVE_STREAM;
        } else {
            streamType = StreamType.VIDEO_STREAM;
        }
    }

    @Nullable
    @Override
    public MultiInfoItemsCollector getRelatedItems() throws ExtractionException {
        assertPageFetched();

        if (getAgeLimit() != NO_AGE_LIMIT) {
            return null;
        }

        try {
            final MultiInfoItemsCollector collector = new MultiInfoItemsCollector(getServiceId());

            final JsonArray results = nextResponse
                    .getObject("contents")
                    .getObject("twoColumnWatchNextResults")
                    .getObject("secondaryResults")
                    .getObject("secondaryResults")
                    .getArray("results");

            final TimeAgoParser timeAgoParser = getTimeAgoParser();
            results.stream()
                    .filter(JsonObject.class::isInstance)
                    .map(JsonObject.class::cast)
                    .map(result -> {
                        if (result.has("compactVideoRenderer")) {
                            return new YoutubeStreamInfoItemExtractor(
                                    result.getObject("compactVideoRenderer"), timeAgoParser);
                        } else if (result.has("compactRadioRenderer")) {
                            return new YoutubeMixOrPlaylistInfoItemExtractor(
                                    result.getObject("compactRadioRenderer"));
                        } else if (result.has("compactPlaylistRenderer")) {
                            return new YoutubeMixOrPlaylistInfoItemExtractor(
                                    result.getObject("compactPlaylistRenderer"));
                        } else if (result.has("lockupViewModel")) {
                            final JsonObject lockupViewModel = result.getObject("lockupViewModel");
                            if ("LOCKUP_CONTENT_TYPE_PLAYLIST".equals(
                                    lockupViewModel.getString("contentType"))) {
                                return new YoutubeMixOrPlaylistLockupInfoItemExtractor(
                                        lockupViewModel);
                            }
                        }
                        return null;
                    })
                    .filter(Objects::nonNull)
                    .forEach(collector::commit);

            return collector;
        } catch (final Exception e) {
            throw new ParsingException("Could not get related videos", e);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getErrorMessage() {
        try {
            return getTextFromObject(playerResponse.getObject(PLAYABILITY_STATUS)
                    .getObject("errorScreen").getObject("playerErrorMessageRenderer")
                    .getObject("reason"));
        } catch (final NullPointerException e) {
            return null; // No error message
        }
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Fetch page
    //////////////////////////////////////////////////////////////////////////*/

    private static final String FORMATS = "formats";
    private static final String ADAPTIVE_FORMATS = "adaptiveFormats";
    private static final String STREAMING_DATA = "streamingData";
    private static final String NEXT = "next";
    private static final String SIGNATURE_CIPHER = "signatureCipher";
    private static final String CIPHER = "cipher";
    private static final String PLAYER_CAPTIONS_TRACKLIST_RENDERER
            = "playerCaptionsTracklistRenderer";
    private static final String CAPTIONS = "captions";
    private static final String PLAYABILITY_STATUS = "playabilityStatus";

    // ttd-edit-v1
//    @Override
//    public void onFetchPage(@Nonnull final Downloader downloader)
//            throws IOException, ExtractionException {
//
//        // Sử dụng CompletableFuture để thực hiện các tác vụ song song
//        final CompletableFuture<String> videoIdFuture = CompletableFuture.supplyAsync(() -> {
//            try {
//                return getId();
//            } catch (final ParsingException e) {
//                // Xử lý ngoại lệ, có thể ghi log hoặc trả về giá trị mặc định
//                LOG.error("e: ", e);
//                return "defaultVideoId"; // Giá trị mặc định
//            }
//        });
//        final CompletableFuture<Localization> localizationFuture =
//                CompletableFuture.supplyAsync(this::getExtractorLocalization);
//        final CompletableFuture<ContentCountry> contentCountryFuture =
//                CompletableFuture.supplyAsync(this::getExtractorContentCountry);
//
//        // Chờ tất cả các tác vụ hoàn thành
//        final String videoId = videoIdFuture.join();
//        final Localization localization = localizationFuture.join();
//        final ContentCountry contentCountry = contentCountryFuture.join();
//
//        final PoTokenProvider poTokenproviderInstance = poTokenProvider;
//        final boolean noPoTokenProviderSet = poTokenproviderInstance == null;
//
//        fetchHtml5Client(localization, contentCountry, videoId, poTokenproviderInstance,
//                noPoTokenProviderSet);
//
//        setStreamType();
//
//        final PoTokenResult androidPoTokenResult = noPoTokenProviderSet ? null
//                : poTokenproviderInstance.getAndroidClientPoToken(videoId);
//
//        fetchAndroidClient(localization, contentCountry, videoId, androidPoTokenResult);
//
//        if (fetchIosClient) {
//            final PoTokenResult iosPoTokenResult = noPoTokenProviderSet ? null
//                    : poTokenproviderInstance.getIosClientPoToken(videoId);
//            fetchIosClient(localization, contentCountry, videoId, iosPoTokenResult);
//        }
//
//        final byte[] nextBody = JsonWriter.string(
//                prepareDesktopJsonBuilder(localization, contentCountry)
//                        .value(VIDEO_ID, videoId)
//                        .value(CONTENT_CHECK_OK, true)
//                        .value(RACY_CHECK_OK, true)
//                        .done())
//                .getBytes(StandardCharsets.UTF_8);
//        nextResponse = getJsonPostResponse(NEXT, nextBody, localization);
//    }

    @Override
    public void onFetchPage(@Nonnull final Downloader downloader)
            throws IOException, ExtractionException {

        // 1. Tạo ExecutorService để quản lý threads
        final ExecutorService executor = Executors.newFixedThreadPool(4);

        try {
            // 2. Fetch thông tin cơ bản song song
            final CompletableFuture<String> videoIdFuture = CompletableFuture.supplyAsync(() -> {
                try {
                    return getId();
                } catch (final ParsingException e) {
                    LOG.error("Failed to get video ID", e);
                    throw new CompletionException(e);
                }
            }, executor);

            final CompletableFuture<Localization> localizationFuture =
                    CompletableFuture.supplyAsync(this::getExtractorLocalization, executor);

            final CompletableFuture<ContentCountry> contentCountryFuture =
                    CompletableFuture.supplyAsync(this::getExtractorContentCountry, executor);

            // 3. Chờ thông tin cơ bản với timeout
            final String videoId = videoIdFuture.get(3, TimeUnit.SECONDS);
            final Localization localization = localizationFuture.get(3, TimeUnit.SECONDS);
            final ContentCountry contentCountry = contentCountryFuture.get(3, TimeUnit.SECONDS);

            final PoTokenProvider poTokenproviderInstance = poTokenProvider;
            final boolean noPoTokenProviderSet = poTokenproviderInstance == null;

            // 4. Fetch PoTokens song song (nếu cần)
            final CompletableFuture<PoTokenResult> androidTokenFuture = !noPoTokenProviderSet
                    ? CompletableFuture.supplyAsync(
                    () -> poTokenproviderInstance.getAndroidClientPoToken(videoId), executor)
                    : CompletableFuture.completedFuture(null);

            final CompletableFuture<PoTokenResult> iosTokenFuture = (!noPoTokenProviderSet && fetchIosClient)
                    ? CompletableFuture.supplyAsync(
                    () -> poTokenproviderInstance.getIosClientPoToken(videoId), executor)
                    : CompletableFuture.completedFuture(null);

            // 5. Fetch các clients song song
            final CompletableFuture<Void> html5Future = CompletableFuture.runAsync(() -> {
                try {
                    fetchHtml5Client(localization, contentCountry, videoId,
                            poTokenproviderInstance, noPoTokenProviderSet);
                    setStreamType(); // Set ngay sau khi có HTML5 data
                } catch (Exception e) {
                    LOG.error("HTML5 client failed", e);
                    throw new CompletionException(e);
                }
            }, executor);

            final CompletableFuture<Void> androidFuture = CompletableFuture.runAsync(() -> {
                try {
                    final PoTokenResult androidPoTokenResult = androidTokenFuture.get(3, TimeUnit.SECONDS);
                    fetchAndroidClient(localization, contentCountry, videoId, androidPoTokenResult);
                } catch (Exception e) {
                    LOG.debug("Android client failed", e);
                }
            }, executor);

            final CompletableFuture<Void> iosFuture = fetchIosClient
                    ? CompletableFuture.runAsync(() -> {
                try {
                    final PoTokenResult iosPoTokenResult = iosTokenFuture.get(3, TimeUnit.SECONDS);
                    fetchIosClient(localization, contentCountry, videoId, iosPoTokenResult);
                } catch (Exception e) {
                    LOG.debug("iOS client failed", e);
                }
            }, executor)
                    : CompletableFuture.completedFuture(null);

            // 6. Fetch next response song song
            final CompletableFuture<Void> nextResponseFuture = CompletableFuture.runAsync(() -> {
                try {
                    final byte[] nextBody = JsonWriter.string(
                                    prepareDesktopJsonBuilder(localization, contentCountry)
                                            .value(VIDEO_ID, videoId)
                                            .value(CONTENT_CHECK_OK, true)
                                            .value(RACY_CHECK_OK, true)
                                            .done())
                            .getBytes(StandardCharsets.UTF_8);
                    nextResponse = getJsonPostResponse(NEXT, nextBody, localization);
                } catch (Exception e) {
                    LOG.error("Next response fetch failed", e);
                    throw new CompletionException(e);
                }
            }, executor);

            // 7. Chờ các tasks quan trọng hoàn thành với timeout
            try {
                CompletableFuture.allOf(
                        html5Future,      // Bắt buộc
                        nextResponseFuture // Bắt buộc
                ).get(10, TimeUnit.SECONDS);
            } catch (Exception e) {
                throw new ExtractionException("Critical tasks failed", e);
            }

            // 8. Chờ các tasks không quan trọng (với timeout ngắn hơn)
            try {
                CompletableFuture.allOf(
                        androidFuture,
                        iosFuture
                ).get(5, TimeUnit.SECONDS);
            } catch (Exception e) {
                LOG.debug("Some non-critical tasks failed or timed out", e);
            }

        } catch (ExecutionException | InterruptedException | TimeoutException e) {
            throw new RuntimeException(e);
        } finally {
            // 9. Cleanup resources
            executor.shutdown();
            try {
                if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                    executor.shutdownNow();
                }
            } catch (InterruptedException e) {
                executor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }

    private static void checkPlayabilityStatus(@Nonnull final JsonObject playabilityStatus)
            throws ParsingException {
        final String status = playabilityStatus.getString("status");
        if (status == null || status.equalsIgnoreCase("ok")) {
            return;
        }

        final String reason = playabilityStatus.getString("reason");

        if (status.equalsIgnoreCase("login_required")) {
            if (reason == null) {
                final String message = playabilityStatus.getArray("messages").getString(0);
                if (message != null && message.contains("private")) {
                    throw new PrivateContentException("This video is private");
                }
            } else if (reason.contains("age")) {
                throw new AgeRestrictedContentException(
                        "This age-restricted video cannot be watched anonymously");
            }
        }

        if ((status.equalsIgnoreCase("unplayable") || status.equalsIgnoreCase("error"))
                && reason != null) {
            if (reason.contains("Music Premium")) {
                throw new YoutubeMusicPremiumContentException();
            }

            if (reason.contains("payment")) {
                throw new PaidContentException("This video is a paid video");
            }

            if (reason.contains("members-only")) {
                throw new PaidContentException("This video is only available"
                        + " for members of the channel of this video");
            }

            if (reason.contains("unavailable")) {
                final String detailedErrorMessage = getTextFromObject(playabilityStatus
                        .getObject("errorScreen")
                        .getObject("playerErrorMessageRenderer")
                        .getObject("subreason"));
                if (detailedErrorMessage != null && detailedErrorMessage.contains("country")) {
                    throw new GeographicRestrictionException(
                            "This video is not available in client's country.");
                } else {
                    throw new ContentNotAvailableException(
                            Objects.requireNonNullElse(detailedErrorMessage, reason));
                }
            }

            if (reason.contains("age-restricted")) {
                throw new AgeRestrictedContentException(
                        "This age-restricted video cannot be watched anonymously");
            }
        }

        throw new ContentNotAvailableException("Got error: \"" + reason + "\"");
    }

//    private void fetchHtml5Client(@Nonnull final Localization localization,
//                                  @Nonnull final ContentCountry contentCountry,
//                                  @Nonnull final String videoId,
//                                  @Nullable final PoTokenProvider poTokenProviderInstance,
//                                  final boolean noPoTokenProviderSet)
//            throws IOException, ExtractionException {
//        html5Cpn = generateContentPlaybackNonce();
//
//        // Suppress NPE warning as nullability is already checked before and passed with
//        // noPoTokenProviderSet
//        //noinspection DataFlowIssue
//        final PoTokenResult webPoTokenResult = noPoTokenProviderSet ? null
//                : poTokenProviderInstance.getWebClientPoToken(videoId);
//        final JsonObject webPlayerResponse;
//        if (noPoTokenProviderSet || webPoTokenResult == null) {
//            webPlayerResponse = YoutubeStreamHelper.getWebMetadataPlayerResponse(
//                    localization, contentCountry, videoId);
//
//            throwExceptionIfPlayerResponseNotValid(webPlayerResponse, videoId);
//
//            // Save the webPlayerResponse into playerResponse in the case the video cannot be
//            // played, so some metadata can be retrieved
//            playerResponse = webPlayerResponse;
//
//            // The microformat JSON object of the content is only returned on the WEB client,
//            // so we need to store it instead of getting it directly from the playerResponse
//            playerMicroFormatRenderer = playerResponse.getObject("microformat")
//                    .getObject("playerMicroformatRenderer");
//
//            final JsonObject playabilityStatus = webPlayerResponse.getObject(PLAYABILITY_STATUS);
//
//            if (isVideoAgeRestricted(playabilityStatus)) {
//                fetchHtml5EmbedClient(localization, contentCountry, videoId,
//                        noPoTokenProviderSet ? null
//                                : poTokenProviderInstance.getWebEmbedClientPoToken(videoId));
//            } else {
//                checkPlayabilityStatus(playabilityStatus);
//
//                final JsonObject tvHtml5PlayerResponse =
//                        YoutubeStreamHelper.getTvHtml5PlayerResponse(
//                                localization, contentCountry, videoId, html5Cpn,
//                                YoutubeJavaScriptPlayerManager.getSignatureTimestamp(videoId));
//
//                if (isPlayerResponseNotValid(tvHtml5PlayerResponse, videoId)) {
//                    throw new ExtractionException("TVHTML5 player response is not valid");
//                }
//
//                html5StreamingData = tvHtml5PlayerResponse.getObject(STREAMING_DATA);
//                playerCaptionsTracklistRenderer = tvHtml5PlayerResponse.getObject(CAPTIONS)
//                        .getObject(PLAYER_CAPTIONS_TRACKLIST_RENDERER);
//            }
//        } else {
//            webPlayerResponse = YoutubeStreamHelper.getWebFullPlayerResponse(
//                    localization, contentCountry, videoId, html5Cpn, webPoTokenResult,
//                    YoutubeJavaScriptPlayerManager.getSignatureTimestamp(videoId));
//
//            throwExceptionIfPlayerResponseNotValid(webPlayerResponse, videoId);
//
//            // Save the webPlayerResponse into playerResponse in the case the video cannot be
//            // played, so some metadata can be retrieved
//            playerResponse = webPlayerResponse;
//
//            // The microformat JSON object of the content is only returned on the WEB client,
//            // so we need to store it instead of getting it directly from the playerResponse
//            playerMicroFormatRenderer = playerResponse.getObject("microformat")
//                    .getObject("playerMicroformatRenderer");
//
//            final JsonObject playabilityStatus = webPlayerResponse.getObject(PLAYABILITY_STATUS);
//
//            if (isVideoAgeRestricted(playabilityStatus)) {
//                fetchHtml5EmbedClient(localization, contentCountry, videoId,
//                        poTokenProviderInstance.getWebEmbedClientPoToken(videoId));
//            } else {
//                checkPlayabilityStatus(playabilityStatus);
//                html5StreamingData = webPlayerResponse.getObject(STREAMING_DATA);
//                playerCaptionsTracklistRenderer = webPlayerResponse.getObject(CAPTIONS)
//                        .getObject(PLAYER_CAPTIONS_TRACKLIST_RENDERER);
//                html5StreamingUrlsPoToken = webPoTokenResult.streamingDataPoToken;
//            }
//        }
//    }

    private void fetchHtml5Client(@Nonnull final Localization localization,
                                  @Nonnull final ContentCountry contentCountry,
                                  @Nonnull final String videoId,
                                  @Nullable final PoTokenProvider poTokenProviderInstance,
                                  final boolean noPoTokenProviderSet)
            throws IOException, ExtractionException {
        try {
            // 1. Parallel fetch các thông tin cần thiết
            CompletableFuture<String> cpnFuture = CompletableFuture.supplyAsync(
                    () -> generateContentPlaybackNonce()
            );

            CompletableFuture<String> signatureTimestampFuture = CompletableFuture.supplyAsync(() -> {
                try {
                    return YoutubeJavaScriptPlayerManager.getSignatureTimestamp(videoId);
                } catch (Exception e) {
                    LOG.error("Failed to get signature timestamp", e);
                    throw new CompletionException(e);
                }
            }).thenApply(String::valueOf);

            CompletableFuture<PoTokenResult> webTokenFuture = CompletableFuture.supplyAsync(() -> {
                if (noPoTokenProviderSet) return null;
                try {
                    return poTokenProviderInstance.getWebClientPoToken(videoId);
                } catch (Exception e) {
                    LOG.error("Failed to get web token", e);
                    return null;
                }
            });

            // 2. Chờ kết quả với timeout
            html5Cpn = cpnFuture.get(2, TimeUnit.SECONDS);
            final String signatureTimestamp = signatureTimestampFuture.get(2, TimeUnit.SECONDS);
            final PoTokenResult webPoTokenResult = webTokenFuture.get(2, TimeUnit.SECONDS);

            // 3. Fetch web player response
            final JsonObject webPlayerResponse;
            if (noPoTokenProviderSet || webPoTokenResult == null) {
                webPlayerResponse = YoutubeStreamHelper.getWebMetadataPlayerResponse(
                        localization, contentCountry, videoId);
                throwExceptionIfPlayerResponseNotValid(webPlayerResponse, videoId);

                // Save response and extract microformat
                playerResponse = webPlayerResponse;
                playerMicroFormatRenderer = playerResponse.getObject("microformat")
                        .getObject("playerMicroformatRenderer");

                final JsonObject playabilityStatus = webPlayerResponse.getObject(PLAYABILITY_STATUS);

                if (isVideoAgeRestricted(playabilityStatus)) {
                    CompletableFuture<PoTokenResult> embedTokenFuture = CompletableFuture.supplyAsync(() -> {
                        if (noPoTokenProviderSet) return null;
                        try {
                            return poTokenProviderInstance.getWebEmbedClientPoToken(videoId);
                        } catch (Exception e) {
                            LOG.error("Failed to get embed token", e);
                            return null;
                        }
                    });

                    PoTokenResult embedToken = embedTokenFuture.get(2, TimeUnit.SECONDS);
                    fetchHtml5EmbedClient(localization, contentCountry, videoId, embedToken);
                } else {
                    checkPlayabilityStatus(playabilityStatus);

                    CompletableFuture<JsonObject> tvResponseFuture = CompletableFuture.supplyAsync(() -> {
                        try {
                            return YoutubeStreamHelper.getTvHtml5PlayerResponse(
                                    localization, contentCountry, videoId, html5Cpn,
                                    YoutubeJavaScriptPlayerManager.getSignatureTimestamp(videoId));
                        } catch (Exception e) {
                            LOG.error("Failed to get TV response", e);
                            throw new CompletionException(e);
                        }
                    });

                    JsonObject tvHtml5PlayerResponse = tvResponseFuture.get(3, TimeUnit.SECONDS);
                    if (isPlayerResponseNotValid(tvHtml5PlayerResponse, videoId)) {
                        throw new ExtractionException("TVHTML5 player response is not valid");
                    }

                    CompletableFuture.allOf(
                            CompletableFuture.runAsync(() ->
                                    html5StreamingData = tvHtml5PlayerResponse.getObject(STREAMING_DATA)),
                            CompletableFuture.runAsync(() ->
                                    playerCaptionsTracklistRenderer = tvHtml5PlayerResponse.getObject(CAPTIONS)
                                            .getObject(PLAYER_CAPTIONS_TRACKLIST_RENDERER))
                    ).get(1, TimeUnit.SECONDS);
                }
            } else {
                webPlayerResponse = YoutubeStreamHelper.getWebFullPlayerResponse(
                        localization, contentCountry, videoId, html5Cpn, webPoTokenResult,
                        Integer.parseInt(signatureTimestamp));

                throwExceptionIfPlayerResponseNotValid(webPlayerResponse, videoId);

                playerResponse = webPlayerResponse;
                playerMicroFormatRenderer = playerResponse.getObject("microformat")
                        .getObject("playerMicroformatRenderer");

                final JsonObject playabilityStatus = webPlayerResponse.getObject(PLAYABILITY_STATUS);

                if (isVideoAgeRestricted(playabilityStatus)) {
                    CompletableFuture<PoTokenResult> embedTokenFuture = CompletableFuture.supplyAsync(() -> {
                        try {
                            return poTokenProviderInstance.getWebEmbedClientPoToken(videoId);
                        } catch (Exception e) {
                            LOG.error("Failed to get embed token", e);
                            throw new CompletionException(e);
                        }
                    });

                    PoTokenResult embedToken = embedTokenFuture.get(2, TimeUnit.SECONDS);
                    fetchHtml5EmbedClient(localization, contentCountry, videoId, embedToken);
                } else {
                    checkPlayabilityStatus(playabilityStatus);

                    CompletableFuture.allOf(
                            CompletableFuture.runAsync(() ->
                                    html5StreamingData = webPlayerResponse.getObject(STREAMING_DATA)),
                            CompletableFuture.runAsync(() ->
                                    playerCaptionsTracklistRenderer = webPlayerResponse.getObject(CAPTIONS)
                                            .getObject(PLAYER_CAPTIONS_TRACKLIST_RENDERER)),
                            CompletableFuture.runAsync(() ->
                                    html5StreamingUrlsPoToken = webPoTokenResult.streamingDataPoToken)
                    ).get(1, TimeUnit.SECONDS);
                }
            }

        } catch (TimeoutException | InterruptedException | ExecutionException e) {
            throw new ExtractionException("Failed to fetch HTML5 client data", e);
        }
    }

    private static void throwExceptionIfPlayerResponseNotValid(
            @Nonnull final JsonObject webPlayerResponse,
            @Nonnull final String videoId) throws ExtractionException {
        if (isPlayerResponseNotValid(webPlayerResponse, videoId)) {
            // Check the playability status, as private and deleted videos and invalid video
            // IDs do not return the ID provided in the player response
            // When the requested video is playable and a different video ID is returned, it
            // has the OK playability status, meaning the ExtractionException after this check
            // will be thrown
            checkPlayabilityStatus(webPlayerResponse.getObject(PLAYABILITY_STATUS));
            throw new ExtractionException("WEB player response is not valid");
        }
    }

    private void fetchHtml5EmbedClient(@Nonnull final Localization localization,
                                       @Nonnull final ContentCountry contentCountry,
                                       @Nonnull final String videoId,
                                       @Nullable final PoTokenResult webEmbedPoTokenResult)
            throws IOException, ExtractionException {
        html5Cpn = generateContentPlaybackNonce();

        final JsonObject webEmbeddedPlayerResponse =
                YoutubeStreamHelper.getWebEmbeddedPlayerResponse(localization, contentCountry,
                        videoId, html5Cpn, webEmbedPoTokenResult,
                        YoutubeJavaScriptPlayerManager.getSignatureTimestamp(videoId));

        // Save the webEmbeddedPlayerResponse into playerResponse in the case the video cannot be
        // played, so some metadata can be retrieved
        playerResponse = webEmbeddedPlayerResponse;

        // Check if the playability status in the player response, if the age-restriction could not
        // be bypassed, an exception will be thrown
        checkPlayabilityStatus(webEmbeddedPlayerResponse.getObject(PLAYABILITY_STATUS));

        if (isPlayerResponseNotValid(webEmbeddedPlayerResponse, videoId)) {
            throw new ExtractionException("WEB_EMBEDDED_PLAYER player response is not valid");
        }

        html5StreamingData = webEmbeddedPlayerResponse.getObject(STREAMING_DATA);
        playerCaptionsTracklistRenderer = webEmbeddedPlayerResponse.getObject(CAPTIONS)
                .getObject(PLAYER_CAPTIONS_TRACKLIST_RENDERER);
        if (webEmbedPoTokenResult != null) {
            html5StreamingUrlsPoToken = webEmbedPoTokenResult.streamingDataPoToken;
        }
    }

    private void fetchAndroidClient(@Nonnull final Localization localization,
                                    @Nonnull final ContentCountry contentCountry,
                                    @Nonnull final String videoId,
                                    @Nullable final PoTokenResult androidPoTokenResult) {
        try {
            androidCpn = generateContentPlaybackNonce();

            final JsonObject androidPlayerResponse;
            if (androidPoTokenResult == null) {
                androidPlayerResponse = YoutubeStreamHelper.getAndroidReelPlayerResponse(
                        contentCountry, localization, videoId, androidCpn);
            } else {
                androidPlayerResponse = YoutubeStreamHelper.getAndroidPlayerResponse(
                        contentCountry, localization, videoId, androidCpn,
                        androidPoTokenResult);
            }

            if (!isPlayerResponseNotValid(androidPlayerResponse, videoId)) {
                androidStreamingData = androidPlayerResponse.getObject(STREAMING_DATA);

                if (isNullOrEmpty(playerCaptionsTracklistRenderer)) {
                    playerCaptionsTracklistRenderer =
                            androidPlayerResponse.getObject(CAPTIONS)
                                    .getObject(PLAYER_CAPTIONS_TRACKLIST_RENDERER);
                }

                if (androidPoTokenResult != null) {
                    androidStreamingUrlsPoToken = androidPoTokenResult.streamingDataPoToken;
                }
            }
        } catch (final Exception ignored) {
            // Ignore exceptions related to ANDROID client fetch or parsing, as it is not
            // compulsory to play contents
        }
    }

    private void fetchIosClient(@Nonnull final Localization localization,
                                @Nonnull final ContentCountry contentCountry,
                                @Nonnull final String videoId,
                                @Nullable final PoTokenResult iosPoTokenResult) {
        try {
            iosCpn = generateContentPlaybackNonce();

            final JsonObject iosPlayerResponse = YoutubeStreamHelper.getIosPlayerResponse(
                    contentCountry, localization, videoId, iosCpn, iosPoTokenResult);

            if (!isPlayerResponseNotValid(iosPlayerResponse, videoId)) {
                iosStreamingData = iosPlayerResponse.getObject(STREAMING_DATA);

                if (isNullOrEmpty(playerCaptionsTracklistRenderer)) {
                    playerCaptionsTracklistRenderer = iosPlayerResponse.getObject(CAPTIONS)
                            .getObject(PLAYER_CAPTIONS_TRACKLIST_RENDERER);
                }

                if (iosPoTokenResult != null) {
                    iosStreamingUrlsPoToken = iosPoTokenResult.streamingDataPoToken;
                }
            }
        } catch (final Exception ignored) {
            // Ignore exceptions related to IOS client fetch or parsing, as it is not
            // compulsory to play contents
        }
    }

    /**
     * Checks whether a player response is invalid.
     *
     * <p>
     * If YouTube detects that requests come from a third party client, they may replace the real
     * player response by another one of a video saying that this content is not available on this
     * app and to watch it on the latest version of YouTube. This behavior has been observed on the
     * {@code ANDROID} client, see
     * <a href="https://github.com/TeamNewPipe/NewPipe/issues/8713">
     *     https://github.com/TeamNewPipe/NewPipe/issues/8713</a>.
     * </p>
     *
     * <p>
     * YouTube may also sometimes for currently unknown reasons rate-limit an IP, and replace the
     * real one by a player response with a video that says that the requested video is
     * unavailable. This behaviour has been observed in Piped on the InnerTube clients used by the
     * extractor ({@code ANDROID} and {@code WEB} clients) which should apply for all clients, see
     * <a href="https://github.com/TeamPiped/Piped/issues/2487">
     *     https://github.com/TeamPiped/Piped/issues/2487</a>.
     * </p>
     *
     * <p>
     * We can detect this by checking whether the video ID of the player response returned is the
     * same as the one requested by the extractor.
     * </p>
     *
     * @param playerResponse a player response from any client
     * @param videoId        the video ID of the content requested
     * @return whether the video ID of the player response is not equal to the one requested
     */
    private static boolean isPlayerResponseNotValid(
            @Nonnull final JsonObject playerResponse,
            @Nonnull final String videoId) {
        return !videoId.equals(playerResponse.getObject("videoDetails")
                .getString("videoId"));
    }

    private static boolean isVideoAgeRestricted(@Nonnull final JsonObject playabilityStatus) {
        // This is language dependent
        return "login_required".equalsIgnoreCase(playabilityStatus.getString("status"))
                && playabilityStatus.getString("reason", "")
                .contains("age");
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Utils
    //////////////////////////////////////////////////////////////////////////*/

    @Nonnull
    private JsonObject getVideoPrimaryInfoRenderer() {
        if (videoPrimaryInfoRenderer != null) {
            return videoPrimaryInfoRenderer;
        }

        videoPrimaryInfoRenderer = getVideoInfoRenderer("videoPrimaryInfoRenderer");
        return videoPrimaryInfoRenderer;
    }

    @Nonnull
    private JsonObject getVideoSecondaryInfoRenderer() {
        if (videoSecondaryInfoRenderer != null) {
            return videoSecondaryInfoRenderer;
        }

        videoSecondaryInfoRenderer = getVideoInfoRenderer("videoSecondaryInfoRenderer");
        return videoSecondaryInfoRenderer;
    }

    @Nonnull
    private JsonObject getVideoInfoRenderer(@Nonnull final String videoRendererName) {
        return nextResponse.getObject("contents")
                .getObject("twoColumnWatchNextResults")
                .getObject("results")
                .getObject("results")
                .getArray("contents")
                .stream()
                .filter(JsonObject.class::isInstance)
                .map(JsonObject.class::cast)
                .filter(content -> content.has(videoRendererName))
                .map(content -> content.getObject(videoRendererName))
                .findFirst()
                .orElse(new JsonObject());
    }

    @Nonnull
    private <T extends Stream> List<T> getItags(
            final String streamingDataKey,
            final ItagItem.ItagType itagTypeWanted,
            final java.util.function.Function<ItagInfo, T> streamBuilderHelper,
            final String streamTypeExceptionMessage) throws ParsingException {
        try {
            final String videoId = getId();
            final List<T> streamList = new ArrayList<>();

            java.util.stream.Stream.of(
                    /*
                    Use the html5StreamingData object first because YouTube should have less
                    control on HTML5 clients, especially for poTokens

                    The androidStreamingData is used as second way as the Android client extraction
                    is more likely to break

                    As iOS streaming data is affected by poTokens and not passing them should lead
                    to 403 responses, it should be used in the last resort
                     */
                    new Pair<>(html5StreamingData,
                            new Pair<>(html5Cpn, html5StreamingUrlsPoToken)),
                    new Pair<>(androidStreamingData,
                            new Pair<>(androidCpn, androidStreamingUrlsPoToken)),
                    new Pair<>(iosStreamingData,
                            new Pair<>(iosCpn, iosStreamingUrlsPoToken)))
                    .flatMap(pair -> getStreamsFromStreamingDataKey(
                            videoId,
                            pair.getFirst(),
                            streamingDataKey,
                            itagTypeWanted,
                            pair.getSecond().getFirst(),
                            pair.getSecond().getSecond()))
                    .map(streamBuilderHelper)
                    .forEachOrdered(stream -> {
                        if (!Stream.containSimilarStream(stream, streamList)) {
                            streamList.add(stream);
                        }
                    });

            return streamList;
        } catch (final Exception e) {
            throw new ParsingException(
                    "Could not get " + streamTypeExceptionMessage + " streams", e);
        }
    }

    /**
     * Get the stream builder helper which will be used to build {@link AudioStream}s in
     * {@link #getItags(String, ItagItem.ItagType, java.util.function.Function, String)}
     *
     * <p>
     * The {@code StreamBuilderHelper} will set the following attributes in the
     * {@link AudioStream}s built:
     * <ul>
     *     <li>the {@link ItagItem}'s id of the stream as its id;</li>
     *     <li>{@link ItagInfo#getContent()} and {@link ItagInfo#getIsUrl()} as its content and
     *     as the value of {@code isUrl};</li>
     *     <li>the media format returned by the {@link ItagItem} as its media format;</li>
     *     <li>its average bitrate with the value returned by {@link
     *     ItagItem#getAverageBitrate()};</li>
     *     <li>the {@link ItagItem};</li>
     *     <li>the {@link DeliveryMethod#DASH DASH delivery method}, for OTF streams, live streams
     *     and ended streams.</li>
     * </ul>
     * </p>
     *
     * <p>
     * Note that the {@link ItagItem} comes from an {@link ItagInfo} instance.
     * </p>
     *
     * @return a stream builder helper to build {@link AudioStream}s
     */
    @Nonnull
    private java.util.function.Function<ItagInfo, AudioStream> getAudioStreamBuilderHelper() {
        return (itagInfo) -> {
            final ItagItem itagItem = itagInfo.getItagItem();
            final AudioStream.Builder builder = new AudioStream.Builder()
                    .setId(String.valueOf(itagItem.id))
                    .setContent(itagInfo.getContent(), itagInfo.getIsUrl())
                    .setMediaFormat(itagItem.getMediaFormat())
                    .setAverageBitrate(itagItem.getAverageBitrate())
                    .setAudioTrackId(itagItem.getAudioTrackId())
                    .setAudioTrackName(itagItem.getAudioTrackName())
                    .setAudioLocale(itagItem.getAudioLocale())
                    .setAudioTrackType(itagItem.getAudioTrackType())
                    .setItagItem(itagItem);

            if (streamType == StreamType.LIVE_STREAM
                    || streamType == StreamType.POST_LIVE_STREAM
                    || !itagInfo.getIsUrl()) {
                // For YouTube videos on OTF streams and for all streams of post-live streams
                // and live streams, only the DASH delivery method can be used.
                builder.setDeliveryMethod(DeliveryMethod.DASH);
            }

            return builder.build();
        };
    }

    /**
     * Get the stream builder helper which will be used to build {@link VideoStream}s in
     * {@link #getItags(String, ItagItem.ItagType, java.util.function.Function, String)}
     *
     * <p>
     * The {@code StreamBuilderHelper} will set the following attributes in the
     * {@link VideoStream}s built:
     * <ul>
     *     <li>the {@link ItagItem}'s id of the stream as its id;</li>
     *     <li>{@link ItagInfo#getContent()} and {@link ItagInfo#getIsUrl()} as its content and
     *     as the value of {@code isUrl};</li>
     *     <li>the media format returned by the {@link ItagItem} as its media format;</li>
     *     <li>whether it is video-only with the {@code areStreamsVideoOnly} parameter</li>
     *     <li>the {@link ItagItem};</li>
     *     <li>the resolution, by trying to use, in this order:
     *         <ol>
     *             <li>the height returned by the {@link ItagItem} + {@code p} + the frame rate if
     *             it is more than 30;</li>
     *             <li>the default resolution string from the {@link ItagItem};</li>
     *             <li>an empty string.</li>
     *         </ol>
     *     </li>
     *     <li>the {@link DeliveryMethod#DASH DASH delivery method}, for OTF streams, live streams
     *     and ended streams.</li>
     * </ul>
     *
     * <p>
     * Note that the {@link ItagItem} comes from an {@link ItagInfo} instance.
     * </p>
     *
     * @param areStreamsVideoOnly whether the stream builder helper will set the video
     *                            streams as video-only streams
     * @return a stream builder helper to build {@link VideoStream}s
     */
    @Nonnull
    private java.util.function.Function<ItagInfo, VideoStream> getVideoStreamBuilderHelper(
            final boolean areStreamsVideoOnly) {
        return (itagInfo) -> {
            final ItagItem itagItem = itagInfo.getItagItem();
            final VideoStream.Builder builder = new VideoStream.Builder()
                    .setId(String.valueOf(itagItem.id))
                    .setContent(itagInfo.getContent(), itagInfo.getIsUrl())
                    .setMediaFormat(itagItem.getMediaFormat())
                    .setIsVideoOnly(areStreamsVideoOnly)
                    .setItagItem(itagItem);

            final String resolutionString = itagItem.getResolutionString();
            builder.setResolution(resolutionString != null ? resolutionString
                    : "");

            if (streamType != StreamType.VIDEO_STREAM || !itagInfo.getIsUrl()) {
                // For YouTube videos on OTF streams and for all streams of post-live streams
                // and live streams, only the DASH delivery method can be used.
                builder.setDeliveryMethod(DeliveryMethod.DASH);
            }

            return builder.build();
        };
    }

    @Nonnull
    private java.util.stream.Stream<ItagInfo> getStreamsFromStreamingDataKey(
            final String videoId,
            final JsonObject streamingData,
            final String streamingDataKey,
            @Nonnull final ItagItem.ItagType itagTypeWanted,
            @Nonnull final String contentPlaybackNonce,
            @Nullable final String poToken) {
        if (streamingData == null || !streamingData.has(streamingDataKey)) {
            return java.util.stream.Stream.empty();
        }

        return streamingData.getArray(streamingDataKey).stream()
                .filter(JsonObject.class::isInstance)
                .map(JsonObject.class::cast)
                .map(formatData -> {
                    try {
                        final ItagItem itagItem = ItagItem.getItag(formatData.getInt("itag"));
                        if (itagItem.itagType == itagTypeWanted) {
                            return buildAndAddItagInfoToList(videoId, formatData, itagItem,
                                    itagItem.itagType, contentPlaybackNonce, poToken);
                        }
                    } catch (final ExtractionException ignored) {
                        // If the itag is not supported, the n parameter of HTML5 clients cannot be
                        // decoded or buildAndAddItagInfoToList fails, we end up here
                    }
                    return null;
                })
                .filter(Objects::nonNull);
    }

    private ItagInfo buildAndAddItagInfoToList(
            @Nonnull final String videoId,
            @Nonnull final JsonObject formatData,
            @Nonnull final ItagItem itagItem,
            @Nonnull final ItagItem.ItagType itagType,
            @Nonnull final String contentPlaybackNonce,
            @Nullable final String poToken) throws ExtractionException {
        String streamUrl;
        if (formatData.has("url")) {
            streamUrl = formatData.getString("url");
        } else {
            // This url has an obfuscated signature
            final String cipherString = formatData.getString(CIPHER,
                    formatData.getString(SIGNATURE_CIPHER));
            final var cipher = Parser.compatParseMap(cipherString);
            final String signature = YoutubeJavaScriptPlayerManager.deobfuscateSignature(videoId,
                    cipher.getOrDefault("s", ""));
            streamUrl = cipher.get("url") + "&" + cipher.get("sp") + "=" + signature;
        }

        // Decode the n parameter if it is present
        // If it cannot be decoded, the stream cannot be used as streaming URLs return HTTP 403
        // responses if it has not the right value
        // Exceptions thrown by
        // YoutubeJavaScriptPlayerManager.getUrlWithThrottlingParameterDeobfuscated are so
        // propagated to the parent which ignores streams in this case
        streamUrl = YoutubeJavaScriptPlayerManager.getUrlWithThrottlingParameterDeobfuscated(
                videoId, streamUrl);

        // Add the content playback nonce to the stream URL
        streamUrl += "&" + CPN + "=" + contentPlaybackNonce;

        // Add the poToken, if there is one
        if (poToken != null) {
            streamUrl += "&pot=" + poToken;
        }

        final JsonObject initRange = formatData.getObject("initRange");
        final JsonObject indexRange = formatData.getObject("indexRange");
        final String mimeType = formatData.getString("mimeType", "");
        final String codec = mimeType.contains("codecs")
                ? mimeType.split("\"")[1] : "";

        itagItem.setBitrate(formatData.getInt("bitrate"));
        itagItem.setWidth(formatData.getInt("width"));
        itagItem.setHeight(formatData.getInt("height"));
        itagItem.setInitStart(Integer.parseInt(initRange.getString("start", "-1")));
        itagItem.setInitEnd(Integer.parseInt(initRange.getString("end", "-1")));
        itagItem.setIndexStart(Integer.parseInt(indexRange.getString("start", "-1")));
        itagItem.setIndexEnd(Integer.parseInt(indexRange.getString("end", "-1")));
        itagItem.setQuality(formatData.getString("quality"));
        itagItem.setCodec(codec);

        if (streamType == StreamType.LIVE_STREAM || streamType == StreamType.POST_LIVE_STREAM) {
            itagItem.setTargetDurationSec(formatData.getInt("targetDurationSec"));
        }

        if (itagType == ItagItem.ItagType.VIDEO || itagType == ItagItem.ItagType.VIDEO_ONLY) {
            itagItem.setFps(formatData.getInt("fps"));
        } else if (itagType == ItagItem.ItagType.AUDIO) {
            // YouTube return the audio sample rate as a string
            itagItem.setSampleRate(Integer.parseInt(formatData.getString("audioSampleRate")));
            itagItem.setAudioChannels(formatData.getInt("audioChannels",
                    // Most audio streams have two audio channels, so use this value if the real
                    // count cannot be extracted
                    // Doing this prevents an exception when generating the
                    // AudioChannelConfiguration element of DASH manifests of audio streams in
                    // YoutubeDashManifestCreatorUtils
                    2));

            final String audioTrackId = formatData.getObject("audioTrack")
                    .getString("id");
            if (!isNullOrEmpty(audioTrackId)) {
                itagItem.setAudioTrackId(audioTrackId);
                final int audioTrackIdLastLocaleCharacter = audioTrackId.indexOf(".");
                if (audioTrackIdLastLocaleCharacter != -1) {
                    // Audio tracks IDs are in the form LANGUAGE_CODE.TRACK_NUMBER
                    LocaleCompat.forLanguageTag(
                            audioTrackId.substring(0, audioTrackIdLastLocaleCharacter)
                    ).ifPresent(itagItem::setAudioLocale);
                }
                itagItem.setAudioTrackType(YoutubeParsingHelper.extractAudioTrackType(streamUrl));
            }

            itagItem.setAudioTrackName(formatData.getObject("audioTrack")
                    .getString("displayName"));
        }

        // YouTube return the content length and the approximate duration as strings
        itagItem.setContentLength(Long.parseLong(formatData.getString("contentLength",
                String.valueOf(CONTENT_LENGTH_UNKNOWN))));
        itagItem.setApproxDurationMs(Long.parseLong(formatData.getString("approxDurationMs",
                String.valueOf(APPROX_DURATION_MS_UNKNOWN))));

        final ItagInfo itagInfo = new ItagInfo(streamUrl, itagItem);

        if (streamType == StreamType.VIDEO_STREAM) {
            itagInfo.setIsUrl(!formatData.getString("type", "")
                    .equalsIgnoreCase("FORMAT_STREAM_TYPE_OTF"));
        } else {
            // We are currently not able to generate DASH manifests for running
            // livestreams, so because of the requirements of StreamInfo
            // objects, return these streams as DASH URL streams (even if they
            // are not playable).
            // Ended livestreams are returned as non URL streams
            itagInfo.setIsUrl(streamType != StreamType.POST_LIVE_STREAM);
        }

        return itagInfo;
    }


    /**
     * {@inheritDoc}
     * Should return a list of Frameset object that contains preview of stream frames
     *
     * <p><b>Warning:</b> When using this method be aware
     * that the YouTube API very rarely returns framesets,
     * that are slightly too small e.g. framesPerPageX = 5, frameWidth = 160, but the url contains
     * a storyboard that is only 795 pixels wide (5*160 &gt; 795). You will need to handle this
     * "manually" to avoid errors.</p>
     *
     * @see <a href="https://github.com/TeamNewPipe/NewPipe/pull/11596">
     *     TeamNewPipe/NewPipe#11596</a>
     */
    @Nonnull
    @Override
    public List<Frameset> getFrames() throws ExtractionException {
        try {
            final JsonObject storyboards = playerResponse.getObject("storyboards");
            final JsonObject storyboardsRenderer = storyboards.getObject(
                    storyboards.has("playerLiveStoryboardSpecRenderer")
                            ? "playerLiveStoryboardSpecRenderer"
                            : "playerStoryboardSpecRenderer"
            );

            if (storyboardsRenderer == null) {
                return Collections.emptyList();
            }

            final String storyboardsRendererSpec = storyboardsRenderer.getString("spec");
            if (storyboardsRendererSpec == null) {
                return Collections.emptyList();
            }

            final String[] spec = storyboardsRendererSpec.split("\\|");
            final String url = spec[0];
            final List<Frameset> result = new ArrayList<>(spec.length - 1);

            for (int i = 1; i < spec.length; ++i) {
                final String[] parts = spec[i].split("#");
                if (parts.length != 8 || Integer.parseInt(parts[5]) == 0) {
                    continue;
                }
                final int totalCount = Integer.parseInt(parts[2]);
                final int framesPerPageX = Integer.parseInt(parts[3]);
                final int framesPerPageY = Integer.parseInt(parts[4]);
                final String baseUrl = url.replace("$L", String.valueOf(i - 1))
                        .replace("$N", parts[6]) + "&sigh=" + parts[7];
                final List<String> urls;
                if (baseUrl.contains("$M")) {
                    final int totalPages = (int) Math.ceil(totalCount / (double)
                            (framesPerPageX * framesPerPageY));
                    urls = new ArrayList<>(totalPages);
                    for (int j = 0; j < totalPages; j++) {
                        urls.add(baseUrl.replace("$M", String.valueOf(j)));
                    }
                } else {
                    urls = Collections.singletonList(baseUrl);
                }
                result.add(new Frameset(
                        urls,
                        /*frameWidth=*/Integer.parseInt(parts[0]),
                        /*frameHeight=*/Integer.parseInt(parts[1]),
                        totalCount,
                        /*durationPerFrame=*/Integer.parseInt(parts[5]),
                        framesPerPageX,
                        framesPerPageY
                ));
            }
            return result;
        } catch (final Exception e) {
            throw new ExtractionException("Could not get frames", e);
        }
    }

    @Nonnull
    @Override
    public Privacy getPrivacy() {
        return playerMicroFormatRenderer.getBoolean("isUnlisted")
                ? Privacy.UNLISTED
                : Privacy.PUBLIC;
    }

    @Nonnull
    @Override
    public String getCategory() {
        return playerMicroFormatRenderer.getString("category", "");
    }

    @Nonnull
    @Override
    public String getLicence() throws ParsingException {
        final JsonObject metadataRowRenderer = getVideoSecondaryInfoRenderer()
                .getObject("metadataRowContainer")
                .getObject("metadataRowContainerRenderer")
                .getArray("rows")
                .getObject(0)
                .getObject("metadataRowRenderer");

        final JsonArray contents = metadataRowRenderer.getArray("contents");
        final String license = getTextFromObject(contents.getObject(0));
        return license != null
                && "Licence".equals(getTextFromObject(metadataRowRenderer.getObject("title")))
                ? license
                : "YouTube licence";
    }

    @Override
    public Locale getLanguageInfo() {
        return null;
    }

    @Nonnull
    @Override
    public List<String> getTags() {
        return JsonUtils.getStringListFromJsonArray(playerResponse.getObject("videoDetails")
                .getArray("keywords"));
    }

    @Nonnull
    @Override
    public List<StreamSegment> getStreamSegments() throws ParsingException {

        if (!nextResponse.has("engagementPanels")) {
            return Collections.emptyList();
        }

        final JsonArray segmentsArray = nextResponse.getArray("engagementPanels")
                .stream()
                // Check if object is a JsonObject
                .filter(JsonObject.class::isInstance)
                .map(JsonObject.class::cast)
                // Check if the panel is the correct one
                .filter(panel -> "engagement-panel-macro-markers-description-chapters".equals(
                        panel
                                .getObject("engagementPanelSectionListRenderer")
                                .getString("panelIdentifier")))
                // Extract the data
                .map(panel -> panel
                        .getObject("engagementPanelSectionListRenderer")
                        .getObject("content")
                        .getObject("macroMarkersListRenderer")
                        .getArray("contents"))
                .findFirst()
                .orElse(null);

        // If no data was found exit
        if (segmentsArray == null) {
            return Collections.emptyList();
        }

        final long duration = getLength();
        final List<StreamSegment> segments = new ArrayList<>();
        for (final JsonObject segmentJson : segmentsArray.stream()
                .filter(JsonObject.class::isInstance)
                .map(JsonObject.class::cast)
                .map(object -> object.getObject("macroMarkersListItemRenderer"))
                .collect(Collectors.toList())
        ) {
            final int startTimeSeconds = segmentJson.getObject("onTap")
                    .getObject("watchEndpoint").getInt("startTimeSeconds", -1);

            if (startTimeSeconds == -1) {
                throw new ParsingException("Could not get stream segment start time.");
            }
            if (startTimeSeconds > duration) {
                break;
            }

            final String title = getTextFromObject(segmentJson.getObject("title"));
            if (isNullOrEmpty(title)) {
                throw new ParsingException("Could not get stream segment title.");
            }

            final StreamSegment segment = new StreamSegment(title, startTimeSeconds);
            segment.setUrl(getUrl() + "?t=" + startTimeSeconds);
            if (segmentJson.has("thumbnail")) {
                final JsonArray previewsArray = segmentJson
                        .getObject("thumbnail")
                        .getArray("thumbnails");
                if (!previewsArray.isEmpty()) {
                    // Assume that the thumbnail with the highest resolution is at the last position
                    final String url = previewsArray
                            .getObject(previewsArray.size() - 1)
                            .getString("url");
                    segment.setPreviewUrl(fixThumbnailUrl(url));
                }
            }
            segments.add(segment);
        }
        return segments;
    }

    @Nonnull
    @Override
    public List<MetaInfo> getMetaInfo() throws ParsingException {
        return YoutubeMetaInfoHelper.getMetaInfo(nextResponse
                .getObject("contents")
                .getObject("twoColumnWatchNextResults")
                .getObject("results")
                .getObject("results")
                .getArray("contents"));
    }

    /**
     * Set the {@link PoTokenProvider} instance to be used for fetching {@code poToken}s.
     *
     * <p>
     * This method allows setting an implementation of {@link PoTokenProvider} which will be used
     * to obtain poTokens required for YouTube player requests and streaming URLs. These tokens
     * are used by YouTube to verify the integrity of the user's device or browser and are required
     * for playback with several clients.
     * </p>
     *
     * <p>
     * Without a {@link PoTokenProvider}, the extractor makes its best effort to fetch as many
     * streams as possible, but without {@code poToken}s, some formats may be not available or
     * fetching may be slower due to additional requests done to get streams.
     * </p>
     *
     * <p>
     * Note that any provider change will be only applied on the next {@link #fetchPage()} request.
     * </p>
     *
     * @param poTokenProvider the {@link PoTokenProvider} instance to set, which can be null to
     *                        remove a provider already passed
     * @see PoTokenProvider
     */
    @SuppressWarnings("unused")
    public static void setPoTokenProvider(@Nullable final PoTokenProvider poTokenProvider) {
        YoutubeStreamExtractor.poTokenProvider = poTokenProvider;
    }

    /**
     * Set whether to fetch the iOS player responses.
     *
     * <p>
     * This method allows fetching the iOS player response, which can be useful in scenarios where
     * streams from the iOS player response are needed, especially HLS manifests.
     * </p>
     *
     * <p>
     * Note that at the time of writing, YouTube is rolling out a {@code poToken} requirement on
     * this client, formats from HLS manifests do not seem to be affected.
     * </p>
     *
     * @param fetchIosClient whether to fetch the iOS client
     */
    @SuppressWarnings("unused")
    public static void setFetchIosClient(final boolean fetchIosClient) {
        YoutubeStreamExtractor.fetchIosClient = fetchIosClient;
    }
}
