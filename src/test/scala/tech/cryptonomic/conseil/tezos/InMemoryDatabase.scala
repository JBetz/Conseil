package tech.cryptonomic.conseil.tezos

import com.typesafe.config.ConfigFactory
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach, TestSuite}
import slick.jdbc.H2Profile.api._

import scala.concurrent.Await
import scala.concurrent.duration._

/**
  * Provides access to a test in-memory database initialized with conseil schema
  */
trait InMemoryDatabase extends BeforeAndAfterAll with BeforeAndAfterEach {
  self: TestSuite =>

  /** defines configuration for a randomly named h2 in-memory instance */
  protected val confString =
    s"""conseildb = {
       |    url = "jdbc:h2:mem:conseil-test;MODE=PostgreSQL;DATABASE_TO_UPPER=false;DB_CLOSE_DELAY=-1"
       |    driver              = org.h2.Driver
       |    connectionPool      = disabled
       |    keepAliveConnection = true
       |  }
    """.stripMargin

  val dbHandler: Database = Database.forConfig("conseildb", config = ConfigFactory.parseString(confString))

  //keep in mind that this is sorted to preserve key consistency
  protected val allTables= Seq(
    Tables.Blocks,
    Tables.OperationGroups,
    Tables.Operations,
    Tables.Accounts,
    Tables.Fees
  )

  /**
    * calling deletes manually is needed to obviate the fact
    * that TRUNCATE TABLE won't work on H2 when there are table constraints
    */
  protected val truncateAll = DBIO.sequence(
    allTables.reverse.map(_.delete)
  )

  protected val dbSchema =
    allTables.map(_.schema).reduce(_ ++ _)

  override protected def beforeAll(): Unit = {
    super.beforeAll()
    Await.result(dbHandler.run(dbSchema.create), 1.second)
  }

  override protected def afterAll(): Unit = {
    Await.ready(dbHandler.run(dbSchema.drop), 1.second)
    dbHandler.close()
    super.afterAll()
  }

  override protected def beforeEach(): Unit = {
    Await.ready(dbHandler.run(truncateAll), 1.second)
    super.beforeEach()
  }

}
