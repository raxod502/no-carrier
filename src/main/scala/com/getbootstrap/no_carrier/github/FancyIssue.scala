package com.getbootstrap.no_carrier.github

import java.time.{Clock, Instant, Duration}
import com.jcabi.github.Issue
import com.getbootstrap.no_carrier.util._
import com.getbootstrap.no_carrier.github.util._
import InstantOrdering._

class FancyIssue(val issue: Issue, val label: String, val timeout: Duration)(implicit clock: Clock) {
  lazy val lastLabelledAt: Option[Instant] = issue.lastLabelledWithAt(label)
  lazy val lastCommentedOnAt: Option[Instant] = issue.commentsIterable.lastOption.map{ _.smart.createdAt.toInstant }
  lazy val lastClosedAt: Option[Instant] = issue.smart.lastClosure.map{ _.smart.createdAt.toInstant }
  lazy val lastReopenedAt: Option[Instant] = issue.smart.lastReopening.map{ _.smart.createdAt.toInstant }
  lazy val lastActionedAt: Option[Instant] = Seq(lastLabelledAt, lastCommentedOnAt, lastClosedAt, lastReopenedAt).max
  lazy val elapsed: Option[Duration] = lastActionedAt.map { Instant.now(clock) - _ }
  lazy val isPastDeadline: Boolean = {
    import DurationOrdering._
    elapsed.exists{ _ > timeout }
  }
  lazy val opNeverDelivered: Boolean = {
    val res = issue.smart.isOpen && issue.labels.smart.contains(label) && isPastDeadline
    println(s"will close: ${res}")
    res
  }
}
