package com.github.jayteealao.twitter.components

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Card
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.size.Scale
import coil.size.Size
import com.commit451.coiltransformations.BlurTransformation
import com.commit451.coiltransformations.gpu.PixelationFilterTransformation
import com.commit451.coiltransformations.gpu.SwirlFilterTransformation
import com.github.jayteealao.crumbs.utils.toAnnotatedString
import com.github.jayteealao.twitter.R
import com.github.jayteealao.twitter.models.Tweet
import com.github.jayteealao.twitter.models.TweetData
import com.github.jayteealao.twitter.models.TweetIncludes
import com.github.jayteealao.twitter.models.TweetMedia
import com.github.jayteealao.twitter.models.TweetPublicMetrics
import com.github.jayteealao.twitter.models.TwitterUser
import com.github.jayteealao.twitter.models.toTweetMedia
import com.github.jayteealao.twitter.models.toTwitterUser
import com.github.jayteealao.twitter.models.tweetPublicMetrics
import com.google.accompanist.pager.ExperimentalPagerApi
import com.google.accompanist.pager.HorizontalPager
import com.google.accompanist.pager.HorizontalPagerIndicator
import com.google.accompanist.pager.rememberPagerState

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun TwitterCard(tweet: Tweet = tweetSample) {
    val profileImageUrl = remember {
        tweet.includes?.users
            ?.first { it?.id == tweet.authorId }!!
            .profileImageUrl
    }

    val user = remember {
        tweet.includes?.users
            ?.first { it?.id == tweet.authorId }
    }

    val mediaPhotos = remember {
        tweet.includes?.media?.filter { it.type == "photo" } ?: emptyList()
    }

    Card(
        onClick = { /*TODO*/ },
        modifier = Modifier
            .wrapContentHeight()
            .fillMaxWidth(),
        elevation = 8.dp,
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .padding(8.dp)
                .wrapContentHeight(),
            verticalArrangement = Arrangement.SpaceEvenly
        ) {
            TwitterUserDisplaySmall(user = user!!, profileImageUrl = profileImageUrl ?: "")
            Text(
                text = tweet.text,
                style = MaterialTheme.typography.body1
            )
            TweetMediaViewer(mediaPhotos)
            TweetPublicMetricsDisplay(publicMetrics = tweet.publicMetrics ?: tweetPublicMetrics())
        }
    }
}

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun TwitterCard(tweet: TweetData) {
    val user = remember { tweet.user.toTwitterUser() }
    val profileImageUrl = remember { tweet.user.profileImageUrl }
    val mediaPhotos = remember { tweet.media.map { it.toTweetMedia() }.filter { it.type == "photo" } }
    val video = remember { tweet.media.map { it.toTweetMedia() }.filter { it.type == "video" } }

    Card(
        onClick = { /*TODO*/ },
        modifier = Modifier
            .wrapContentHeight()
            .fillMaxWidth(),
        elevation = 0.dp,
        shape = RoundedCornerShape(16.dp),
        backgroundColor = Color.White
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .wrapContentHeight(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            TwitterUserDisplaySmall(user = user, profileImageUrl = profileImageUrl ?: "")
            Text(
                text = tweet.toAnnotatedString(),
                style = MaterialTheme.typography.body1,
                maxLines = 4
            )
            if (video.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(video[0].width / video[0].height.toFloat(), true)
                        .clip(RoundedCornerShape(12.dp))
                ) {
                    VideoPlayer(uriString = video[0].url ?: "")
                }
            } else if (mediaPhotos.isNotEmpty()) {
                // Show only first image in feed view
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(mediaPhotos[0].url)
                        .size(Size.ORIGINAL)
                        .scale(Scale.FIT)
                        .crossfade(true)
                        .build(),
                    contentDescription = "",
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(mediaPhotos[0].width / mediaPhotos[0].height.toFloat(), true)
                        .clip(RoundedCornerShape(12.dp))
                )
            }
            TweetPublicMetricsDisplay(publicMetrics = tweet.publicMetrics ?: tweetPublicMetrics())
        }
    }
}

@Composable
fun TwitterUserDisplaySmall(user: TwitterUser, profileImageUrl: String) {
    Row(
        modifier = Modifier
            .wrapContentHeight()
            .padding(bottom = 8.dp)
            .fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (profileImageUrl.isNotBlank() == true) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(profileImageUrl)
                    .size(400)
                    .scale(Scale.FIT)
                    .crossfade(true)
                    .build(),
                contentDescription = "",
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
            )
        } else {
            ImagewithGradient(
                painter = painterResource(R.drawable.logo_2),
                contentDescription = "",
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
            )
        }
        Column(
            modifier = Modifier
                .padding(start = 8.dp)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.SpaceEvenly,
            horizontalAlignment = Alignment.Start
        ) {
            Text(
                text = user.name,
                style = MaterialTheme.typography.body1
            )
            Text(
                text = "@${user.username}",
                color = Color.DarkGray,
                style = MaterialTheme.typography.body2
            )
        }
    }
}

@OptIn(ExperimentalPagerApi::class)
@Composable
fun TweetMediaViewer(media: List<TweetMedia> = emptyList()) {
    val pagerState = rememberPagerState()
    HorizontalPager(
        state = pagerState,
        modifier = Modifier
            .fillMaxWidth()
            .wrapContentHeight(),
        count = media.size
    ) {
        val media = media[it]

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min),
            contentAlignment = Alignment.BottomCenter
        ) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(media.url)
                    .size(Size.ORIGINAL)
                    .scale(Scale.FILL)
                    .crossfade(true)
                    .build(),
                contentDescription = "",
                modifier = Modifier
                    .padding(horizontal = 4.dp, vertical = 8.dp)
                    .fillMaxWidth()
                    .aspectRatio(media.width / media.height.toFloat(), true)
                    .clip(
                        RoundedCornerShape(16.dp)
                    )
            )
            HorizontalPagerIndicator(pagerState = pagerState)
        }
    }
//    if (media.isNotEmpty()) {
//        val media0 = media[0]
//        if (media0.type == "photo") {
//            AsyncImage(
//                model = ImageRequest.Builder(LocalContext.current)
//                    .data(media0.url)
//                    .size(Size.ORIGINAL)
//                    .scale(Scale.FIT)
//                    .crossfade(true)
//                    .build(),
//                contentDescription = "",
//                modifier = Modifier
//                    .padding(horizontal = 4.dp, vertical = 8.dp)
//                    .fillMaxWidth()
//                    .aspectRatio(media0.width / media0.height.toFloat(), false)
//                    .clip(
//                        RoundedCornerShape(16.dp)
//                    )
//            )
//        } else {
//            AsyncImage(
//                model = ImageRequest.Builder(LocalContext.current)
//                    .data(media0.previewImageUrl)
//                    .size(400)
//                    .scale(Scale.FIT)
//                    .crossfade(true)
//                    .build(),
//                contentDescription = "",
//                modifier = Modifier
//                    .height(200.dp)
//                    .fillMaxWidth()
//                    .clip(RoundedCornerShape(16.dp))
//            )
//        }
//    }
}

@Composable
fun TweetPublicMetricsDisplay(publicMetrics: TweetPublicMetrics) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        IconButtonWithNumber(
            Modifier.size(16.dp),
            painterResource(id = R.drawable.fa6solidheart),
            publicMetrics.likeCount ?: 0
        )
        IconButtonWithNumber(
            Modifier.size(16.dp),
            painterResource(id = R.drawable.fa6solidcomments),
            publicMetrics.replyCount ?: 0
        )
        IconButtonWithNumber(
            Modifier.size(16.dp),
            painterResource(id = R.drawable.fa6solidretweet),
            publicMetrics.retweetCount ?: 0
        )
        IconButtonWithNumber(
            Modifier.size(16.dp),
            painterResource(id = R.drawable.fa6solidquoteright),
            publicMetrics.quoteCount ?: 0
        )
    }
}

@Composable
fun IconButtonWithNumber(
    modifier: Modifier = Modifier,
    painter: Painter = painterResource(R.drawable.logo_2),
    number: Int = 0
) {
    Row(
        Modifier.padding(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Image(
            painter = painter,
            contentDescription = null,
            modifier = modifier.padding(end = 4.dp),
            colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(Color(0xFF666666))
        )
        Text(
            text = number.toString(),
            style = MaterialTheme.typography.caption,
            color = Color(0xFF666666)
        )
    }
}

@Composable
fun ImagewithGradient(
    modifier: Modifier = Modifier,
    painter: Painter = painterResource(R.drawable.logo_2),
    brush: Brush = Brush.horizontalGradient(
        listOf(Color(0x80F12711), Color(0x80F5AF19))
    ),
    contentDescription: String = ""
) {
    Image(
        painter = painter,
        contentDescription = contentDescription,
        modifier = modifier
//            .size(40.dp)
//            .clip(CircleShape)
            .graphicsLayer(alpha = 0.99f)
            .drawWithContent {
                drawContent()
                drawRect(
                    brush = brush,
                    size = size,
                    blendMode = BlendMode.SrcIn
                )
            }
    )
}

val tweetSample = Tweet(
    id = "123456543",
    text = "Lorem Ipsur Dolor Lorem Ipsur Dolor Lorem Ipsur Dolor " +
        "Lorem Ipsur Dolor Lorem Ipsur Dolor Lorem Ipsur Dolor" +
        "Lorem Ipsur DolorLorem Ipsur Dolor",
    createdAt = "",
    authorId = "123452",
    attachments = null,
    includes = TweetIncludes(
        users = listOf(
            TwitterUser(
                id = "123452",
                name = "Priscillia Shane",
                username = "@shanepriscillia",
                profileImageUrl = "",
                protected = false,
                verified = false
            )
        ),
        media = null,
        tweets = null
    ),
    contextAnnotation = null,
    conversationId = "",
    entities = null,
    inReplyToUserId = null,
    lang = null,
    publicMetrics = TweetPublicMetrics(
        retweetCount = 12,
        replyCount = 23,
        likeCount = 118,
        quoteCount = 22,
        viewCount = 1000
    ),
    referencedTweets = null
)

// @Preview
@Composable
fun PrevImage() {
    ImagewithGradient()
}

@Preview
@Composable
fun PrevTwitterCard() {
    TwitterCard()
}

@RequiresApi(Build.VERSION_CODES.S)
// @Preview
@Composable
fun PreviewTest() {
//    Image(
//        painter = painterResource(R.drawable.logo_2),
//        contentDescription = "",
//        modifier = Modifier
//            .graphicsLayer(
//                alpha = 0.99f,
//                renderEffect = RenderEffect.createBlurEffect(25f,25f, Shader.TileMode.MIRROR).asComposeRenderEffect()
//            )
//            .drawWithContent {
//                drawContent()
//                drawRect(
//                    brush = Brush.horizontalGradient(
//                        listOf(Color(0xFFF12711), Color(0xFFF5AF19))
//                    ),
//                    size = size,
//                    blendMode = BlendMode.SrcIn
//                )
//            }
//    )
//    Box(
//        Modifier
//            .size(100.dp)
//            .graphicsLayer(
//                alpha = 0.99f,
//                renderEffect = RenderEffect
//                    .createBlurEffect(25f, 25f, Shader.TileMode.MIRROR)
//                    .asComposeRenderEffect()
//            )
//            .background(
//                brush = Brush.horizontalGradient(
//                    listOf(Color(0x80F12711), Color(0x80F5AF19))
//                )
//            )
//    )
    val imageRequest = ImageRequest.Builder(LocalContext.current.applicationContext)
        .data(R.drawable.flare)
        .transformations(
            BlurTransformation(
                LocalContext.current.applicationContext,
                25f,
                1f
            ),
            PixelationFilterTransformation(
                LocalContext.current.applicationContext,
                100f
            ),
            SwirlFilterTransformation(
                LocalContext.current.applicationContext
            )
        )
        .scale(Scale.FILL)
        .build()

    AsyncImage(
        model = imageRequest,
//        model = R.drawable.flare,
        contentDescription = ""

    )
}
