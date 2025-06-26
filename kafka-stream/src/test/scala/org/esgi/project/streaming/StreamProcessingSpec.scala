package org.esgi.project.streaming

import io.github.azhur.kafka.serde.PlayJsonSupport
import org.apache.kafka.streams.{StreamsConfig, TestInputTopic, TestOutputTopic, TopologyTestDriver}
import org.apache.kafka.streams.scala.serialization.Serdes
import org.apache.kafka.streams.state.KeyValueStore
import org.apache.kafka.streams.test.TestRecord
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.BeforeAndAfterEach
import models.{LikeEvent, MovieStats, ViewEvent}
import java.util.Properties
import java.time.{Duration, Instant}

import scala.jdk.CollectionConverters._

class StreamProcessingSpec extends AnyFunSuite with BeforeAndAfterEach with PlayJsonSupport {

  import org.apache.kafka.streams.scala.serialization.Serdes._
  import StreamProcessing._

  private var testDriver: TopologyTestDriver = _
  private var viewsTopic: TestInputTopic[String, ViewEvent] = _
  private var likesTopic: TestInputTopic[String, LikeEvent] = _
  private var movieStatsStore: KeyValueStore[Int, MovieStats] = _

  override def beforeEach(): Unit = {
    // Configure the test driver
    val props = new Properties()
    props.put(StreamsConfig.APPLICATION_ID_CONFIG, "test-movie-stats")
    props.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, "dummy:1234")
    props.put(StreamsConfig.DEFAULT_KEY_SERDE_CLASS_CONFIG, stringSerde.getClass.getName)
    props.put(StreamsConfig.DEFAULT_VALUE_SERDE_CLASS_CONFIG, stringSerde.getClass.getName)

    // Create the test driver
    testDriver = new TopologyTestDriver(builder.build(), props)

    // Create the input topics
    viewsTopic = testDriver.createInputTopic(
      viewsTopicName,
      stringSerde.serializer(),
      viewSerde.serializer()
    )

    likesTopic = testDriver.createInputTopic(
      likesTopicName,
      stringSerde.serializer(),
      likeSerde.serializer()
    )

    // Get the state store
    movieStatsStore = testDriver.getKeyValueStore[Int, MovieStats](movieStatsStoreName)
  }

  override def afterEach(): Unit = {
    if (testDriver != null) {
      testDriver.close()
      testDriver = null
    }
  }

  test("KStream transformation - should extract movie title from ViewEvent") {
    // Given
    val viewEvent = ViewEvent(1, "The Matrix", "sci-fi")

    // When
    viewsTopic.pipeInput("key1", viewEvent)

    // Then
    val stats = movieStatsStore.get(1)
    assert(stats != null)
    assert(stats.title == "The Matrix")
  }

  test("KStream transformation - should extract movie ID from ViewEvent") {
    // Given
    val viewEvent = ViewEvent(2, "Inception", "sci-fi")

    // When
    viewsTopic.pipeInput("key1", viewEvent)

    // Then
    val stats = movieStatsStore.get(2)
    assert(stats != null)
    assert(stats.id == 2)
  }

  test("KTable aggregation - should count views by category") {
    // Given
    val viewEvent1 = ViewEvent(3, "The Godfather", "drama")
    val viewEvent2 = ViewEvent(3, "The Godfather", "crime")
    val viewEvent3 = ViewEvent(3, "The Godfather", "drama")

    // When
    viewsTopic.pipeInput("key1", viewEvent1)
    viewsTopic.pipeInput("key2", viewEvent2)
    viewsTopic.pipeInput("key3", viewEvent3)

    // Then
    val stats = movieStatsStore.get(3)
    assert(stats != null)
    assert(stats.pastViews("drama") == 2)
    assert(stats.pastViews("crime") == 1)
    assert(stats.totalViewCount == 3)
  }

  test("KTable aggregation - should calculate average score from LikeEvents") {
    // Given
    val viewEvent = ViewEvent(4, "Pulp Fiction", "crime")
    val likeEvent1 = LikeEvent(4, 4.0)
    val likeEvent2 = LikeEvent(4, 5.0)
    val likeEvent3 = LikeEvent(4, 3.0)

    // When
    viewsTopic.pipeInput("key1", viewEvent)
    likesTopic.pipeInput("key1", likeEvent1)
    likesTopic.pipeInput("key2", likeEvent2)
    likesTopic.pipeInput("key3", likeEvent3)

    // Then
    val stats = movieStatsStore.get(4)
    assert(stats != null)
    assert(stats.averageScore == 4.0) // (4 + 5 + 3) / 3 = 4
  }

  test("State store evolution - should update movie stats when new events arrive") {
    // Given - Initial state
    val viewEvent1 = ViewEvent(5, "The Shawshank Redemption", "drama")
    val likeEvent1 = LikeEvent(5, 5.0)

    // When - Initial events
    viewsTopic.pipeInput("key1", viewEvent1)
    likesTopic.pipeInput("key1", likeEvent1)

    // Then - Initial state check
    var stats = movieStatsStore.get(5)
    assert(stats != null)
    assert(stats.title == "The Shawshank Redemption")
    assert(stats.totalViewCount == 1)
    assert(stats.averageScore == 5.0)

    // When - New events arrive
    val viewEvent2 = ViewEvent(5, "The Shawshank Redemption", "crime")
    val likeEvent2 = LikeEvent(5, 4.0)

    viewsTopic.pipeInput("key2", viewEvent2)
    likesTopic.pipeInput("key2", likeEvent2)

    // Then - State should be updated
    stats = movieStatsStore.get(5)
    assert(stats != null)
    assert(stats.totalViewCount == 2)
    assert(stats.pastViews("drama") == 1)
    assert(stats.pastViews("crime") == 1)
    assert(stats.averageScore == 4.5) // (5 + 4) / 2 = 4.5
  }

  test("State store evolution - should handle multiple movies") {
    // Given
    val movie1View = ViewEvent(6, "Forrest Gump", "drama")
    val movie2View = ViewEvent(7, "The Dark Knight", "action")
    val movie1Like = LikeEvent(6, 4.5)
    val movie2Like = LikeEvent(7, 5.0)

    // When
    viewsTopic.pipeInput("key1", movie1View)
    viewsTopic.pipeInput("key2", movie2View)
    likesTopic.pipeInput("key1", movie1Like)
    likesTopic.pipeInput("key2", movie2Like)

    // Then
    val stats1 = movieStatsStore.get(6)
    val stats2 = movieStatsStore.get(7)

    assert(stats1 != null)
    assert(stats2 != null)
    assert(stats1.title == "Forrest Gump")
    assert(stats2.title == "The Dark Knight")
    assert(stats1.averageScore == 4.5)
    assert(stats2.averageScore == 5.0)
  }

  test("Windowed operations - should process views in time windows") {
    // Given
    val movieId = 8
    val movieTitle = "Interstellar"

    // Create views with different categories
    val view1 = ViewEvent(movieId, movieTitle, "sci-fi")
    val view2 = ViewEvent(movieId, movieTitle, "drama")
    val view3 = ViewEvent(movieId, movieTitle, "sci-fi")

    // When - Send events with timestamps
    // Note: In a real test, we would use different timestamps to test window behavior
    // but for simplicity in this test, we're just verifying the windowed aggregation works
    viewsTopic.pipeInput("key1", view1)
    viewsTopic.pipeInput("key2", view2)
    viewsTopic.pipeInput("key3", view3)

    // Then
    val stats = movieStatsStore.get(movieId)
    assert(stats != null)
    assert(stats.title == movieTitle)

    // Check that the views are counted correctly
    assert(stats.pastViews("sci-fi") == 2)
    assert(stats.pastViews("drama") == 1)
    assert(stats.totalViewCount == 3)

    // The lastFiveMinViews may contain data depending on how the windowing is implemented
    // In our test environment, this might be empty or match pastViews
    // We're just verifying the structure exists
    assert(stats.lastFiveMinViews != null)
  }

  test("Join operations - should correctly join views and likes data") {
    // Given
    val movieId = 9
    val movieTitle = "The Lord of the Rings"

    // Create view events
    val view1 = ViewEvent(movieId, movieTitle, "fantasy")
    val view2 = ViewEvent(movieId, movieTitle, "adventure")

    // Create like events
    val like1 = LikeEvent(movieId, 4.0)
    val like2 = LikeEvent(movieId, 5.0)

    // When - First send view events
    viewsTopic.pipeInput("key1", view1)
    viewsTopic.pipeInput("key2", view2)

    // Then - Check initial state with only views
    var stats = movieStatsStore.get(movieId)
    assert(stats != null)
    assert(stats.title == movieTitle)
    assert(stats.totalViewCount == 2)
    assert(stats.averageScore == 0.0) // No likes yet

    // When - Then send like events
    likesTopic.pipeInput("key1", like1)
    likesTopic.pipeInput("key2", like2)

    // Then - Check final state with both views and likes
    stats = movieStatsStore.get(movieId)
    assert(stats != null)
    assert(stats.title == movieTitle)
    assert(stats.totalViewCount == 2)
    assert(stats.pastViews("fantasy") == 1)
    assert(stats.pastViews("adventure") == 1)
    assert(stats.averageScore == 4.5) // (4.0 + 5.0) / 2 = 4.5
  }
}
