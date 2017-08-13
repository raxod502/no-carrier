package com.getbootstrap.no_carrier

import java.time.{Clock, Duration}
import scala.util.{Success, Failure}
import scala.util.Try
import com.jcabi.github.{Github, Issue}
import com.jcabi.github.Coordinates.{Simple=>RepoId}
import com.typesafe.scalalogging.StrictLogging
import com.getbootstrap.no_carrier.github.{Credentials, FancyIssue}
import com.getbootstrap.no_carrier.github.util._
import com.getbootstrap.no_carrier.http.UserAgent
import com.getbootstrap.no_carrier.util._

case class Arguments(
  creds: Credentials,
  repoId: RepoId,
  label: String,
  timeout: Duration,
  rateLimitThreshold: Int
) {
  private implicit val userAgent = new UserAgent("NoCarrier/0.1 (https://github.com/twbs/no-carrier)")
  lazy val github: Github = creds.github(rateLimitThreshold)
}

object Main extends App with StrictLogging {
  val enabled = true
  implicit val clock = Clock.systemUTC
  val rateLimitThreshold = 10
  val username = EnvVars.getRequired("GITHUB_USERNAME")
  val password = EnvVars.getRequired("GITHUB_PASSWORD")
  val arguments = (args.toSeq match {
    case Seq(RepositoryId(repoId), NonEmptyStr(label), IntFromStr(PositiveInt(dayCount))) => {
      Some(Arguments(
        Credentials(username = username, password = password),
        repoId = repoId,
        label = label,
        timeout = java.time.Duration.ofDays(dayCount),
        rateLimitThreshold = rateLimitThreshold
      ))
    }
    case _ => {
      System.err.println("USAGE: no-carrier <owner/repo> <label> <days>")
      System.exit(1)
      None // dead code
    }
  }).get

  main(arguments)

  def main(args: Arguments) {
    squelchExcessiveLogging()
    logger.info("Started session.")
    val github = args.github
    val rateLimit = github.rateLimit
    val repo = github.repos.get(args.repoId)
    logger.info(s"Repo(${args.repoId}), ${args.creds}, Label(${args.label}), Timeout(${args.timeout})")

    val waitingOnOp = repo.issues.openWithLabel(args.label).map{ issue =>
      new FancyIssue(issue = issue, label = args.label, timeout = args.timeout)
    }
    val opNeverDelivered = waitingOnOp.filter{ issue => {
      logger.info(s"GitHub rate limit status: ${rateLimit.summary}")
      logger.info(s"Checking issue #${issue.issue.number} ...")
      issue.opNeverDelivered
    } }
    val totalClosed = opNeverDelivered.map { issue =>
      if (closeOut(issue.issue, issue.elapsed.get, args.label)) 1 else 0
    }.sum
    logger.info(s"Closed ${totalClosed} issues.")
    logger.info("Session complete; exiting.")
  }

  def closeOut(issue: Issue, elapsed: Duration, label: String): Boolean = {
    logger.info(s"OP never delivered on issue #${issue.number}. Going to close it out.")
    if (enabled) {
      val explanatoryComment =
        s"""This issue is being closed automatically since it has a "${label}" label but hasn't had any activity in ${elapsed.toDays} days. But don't worry: if you have some information to help move the discussion along, just leave a comment and I'll re-open the issue.
           |
           |*(Comment generated automatically by [NO CARRIER](https://github.com/twbs/no-carrier) via [tidier](https://github.com/raxod502/tidier).)*
           |""".stripMargin

      val attempt = Try{ issue.comments.post(explanatoryComment) }.flatMap{ comment => {
        logger.info(s"Posted comment #${comment.number}")
        Try{ issue.smart.close() }
      }}
      attempt match {
        case Success(_) => logger.info(s"Closed issue #${issue.number}")
        case Failure(exc) => logger.error(s"Error when trying to close out issue #${issue.number}", exc)
      }
      attempt.isSuccess
    }
    else {
      false
    }
  }

  def squelchExcessiveLogging() {
    import ch.qos.logback.classic.Level

    val loggersToSquelch = Set(
      "com.jcabi.aspects.aj.NamedThreads",
      "com.jcabi.http.request.BaseRequest",
      "com.jcabi.manifests.Manifests"
    )
    for { loggerName <- loggersToSquelch } {
      loggerName.logger.setLevel(Level.WARN)
    }

    "com.jcabi.github.Issue$Smart".logger.setLevel(Level.OFF)
  }
}
