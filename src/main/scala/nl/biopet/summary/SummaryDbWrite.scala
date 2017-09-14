package nl.biopet.summary

import java.sql.Date

import nl.biopet.summary.Schema._
import nl.biopet.summary.SummaryDb._
import nl.biopet.summary.SummaryDb.Implicts._
import slick.jdbc.H2Profile.api._

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, ExecutionContext, Future}

class SummaryDbWrite(val db: Database)(implicit val ec: ExecutionContext)
    extends SummaryDb {

  /** This method will create all tables */
  def createTables(): Unit = {
    val setup = DBIO.seq(
      (runs.schema ++ samples.schema ++
        libraries.schema ++ pipelines.schema ++
        modules.schema ++ stats.schema ++ settings.schema ++
        files.schema ++ executables.schema).create
    )
    val setupFuture = db.run(setup)
    Await.result(setupFuture, Duration.Inf)
  }

  /** This method will create a new run and return the runId */
  def createRun(runName: String,
                outputDir: String,
                version: String,
                commitHash: String,
                creationDate: Date): Future[Int] = {
    val id = Await.result(db.run(runs.size.result), Duration.Inf)
    db.run(
        runs.forceInsert(
          Run(id, runName, outputDir, version, commitHash, creationDate)))
      .map(_ => id)
  }

  /** This creates a new sample and return the sampleId */
  def createSample(name: String,
                   runId: Int,
                   tags: Option[String] = None): Future[Int] = {
    val id = Await.result(db.run(samples.size.result), Duration.Inf)
    db.run(samples.forceInsert(Sample(id, name, runId, tags))).map(_ => id)
  }

  def createOrUpdateSample(name: String,
                           runId: Int,
                           tags: Option[String] = None): Future[Int] = {
    getSampleId(runId, name).flatMap {
      case Some(id: Int) =>
        db.run(
            samples
              .filter(_.name === name)
              .filter(_.id === id)
              .map(_.tags)
              .update(tags))
          .map(_ => id)
      case _ => createSample(name, runId, tags)
    }
  }

  /** This will create a new library */
  def createLibrary(name: String,
                    runId: Int,
                    sampleId: Int,
                    tags: Option[String] = None): Future[Int] = {
    val id = Await.result(db.run(libraries.size.result), Duration.Inf)
    db.run(libraries.forceInsert(Library(id, name, runId, sampleId, tags)))
      .map(_ => id)
  }

  def createOrUpdateLibrary(name: String,
                            runId: Int,
                            sampleId: Int,
                            tags: Option[String] = None): Future[Int] = {
    getLibraryId(runId, sampleId, name).flatMap {
      case Some(id: Int) =>
        db.run(
            libraries
              .filter(_.name === name)
              .filter(_.id === id)
              .filter(_.sampleId === sampleId)
              .map(_.tags)
              .update(tags))
          .map(_ => id)
      case _ => createLibrary(name, runId, sampleId, tags)
    }
  }

  /** Creates a new pipeline, even if it already exist. This may give a database exeption */
  def forceCreatePipeline(name: String): Future[Int] = {
    val id = Await.result(db.run(pipelines.size.result), Duration.Inf)
    db.run(pipelines.forceInsert(Pipeline(id, name))).map(_ => id)
  }

  /** Creates a new pipeline if it does not yet exist */
  def createPipeline(name: String): Future[Int] = {
    getPipelines(name = Some(name))
      .flatMap { m =>
        if (m.isEmpty) forceCreatePipeline(name)
        else Future(m.head.id)
      }
  }

  /** Creates a new module, even if it already exist. This may give a database exeption */
  def forceCreateModule(name: String, pipelineId: Int): Future[Int] = {
    val id = Await.result(db.run(modules.size.result), Duration.Inf)
    db.run(modules.forceInsert(Module(id, name, pipelineId))).map(_ => id)
  }

  /** Creates a new module if it does not yet exist */
  def createModule(name: String, pipelineId: Int): Future[Int] = {
    getModules(name = Some(name), pipelineId = Some(pipelineId))
      .flatMap { m =>
        if (m.isEmpty) forceCreateModule(name, pipelineId)
        else Future(m.head.id)
      }
  }

  /** Create a new stat in the database, This method is need checking before */
  def createStat(runId: Int,
                 pipelineId: Int,
                 moduleId: Option[Int] = None,
                 sampleId: Option[Int] = None,
                 libId: Option[Int] = None,
                 content: String): Future[Int] = {
    db.run(
      stats.forceInsert(
        Stat(runId, pipelineId, moduleId, sampleId, libId, content)))
  }

  /** This create or update a stat */
  def createOrUpdateStat(runId: Int,
                         pipelineId: Int,
                         moduleId: Option[Int] = None,
                         sampleId: Option[Int] = None,
                         libId: Option[Int] = None,
                         content: String): Future[Int] = {
    val filter = statsFilter(
      Some(runId),
      pipelineId,
      Some(moduleId.map(ModuleId).getOrElse(NoModule)),
      Some(sampleId.map(SampleId).getOrElse(NoSample)),
      Some(libId.map(LibraryId).getOrElse(NoLibrary))
    )
    val r = Await.result(db.run(filter.size.result), Duration.Inf)
    if (r == 0)
      createStat(runId, pipelineId, moduleId, sampleId, libId, content)
    else db.run(filter.map(_.content).update(content))
  }

  /** This method creates a new setting. This method need checking before */
  def createSetting(runId: Int,
                    pipelineId: Int,
                    moduleId: Option[Int] = None,
                    sampleId: Option[Int] = None,
                    libId: Option[Int] = None,
                    content: String): Future[Int] = {
    db.run(
      settings.forceInsert(
        Setting(runId, pipelineId, moduleId, sampleId, libId, content)))
  }

  /** This method creates or update a setting. */
  def createOrUpdateSetting(runId: Int,
                            pipelineId: Int,
                            moduleId: Option[Int] = None,
                            sampleId: Option[Int] = None,
                            libId: Option[Int] = None,
                            content: String): Future[Int] = {
    val filter = settingsFilter(Some(runId),
                                PipelineId(pipelineId),
                                moduleId.map(ModuleId),
                                sampleId.map(SampleId),
                                libId.map(LibraryId))
    val r = Await.result(db.run(filter.size.result), Duration.Inf)
    if (r == 0)
      createSetting(runId, pipelineId, moduleId, sampleId, libId, content)
    else
      db.run(
        filter.update(
          Setting(runId, pipelineId, moduleId, sampleId, libId, content)))
  }

  /** Creates a file. This method will raise exception if it already exist */
  def createFile(runId: Int,
                 pipelineId: Int,
                 moduleId: Option[Int] = None,
                 sampleId: Option[Int] = None,
                 libId: Option[Int] = None,
                 key: String,
                 path: String,
                 md5: String,
                 link: Boolean = false,
                 size: Long): Future[Int] = {
    db.run(
      files.forceInsert(
        Schema.File(runId,
                    pipelineId,
                    moduleId,
                    sampleId,
                    libId,
                    key,
                    path,
                    md5,
                    link,
                    size)))
  }

  /** Create or update a File */
  def createOrUpdateFile(runId: Int,
                         pipelineId: Int,
                         moduleId: Option[Int] = None,
                         sampleId: Option[Int] = None,
                         libId: Option[Int] = None,
                         key: String,
                         path: String,
                         md5: String,
                         link: Boolean = false,
                         size: Long): Future[Int] = {
    val filter = filesFilter(Some(runId),
                             PipelineId(pipelineId),
                             moduleId.map(ModuleId),
                             sampleId.map(SampleId),
                             libId.map(LibraryId),
                             Some(key))
    val r = Await.result(db.run(filter.size.result), Duration.Inf)
    if (r == 0)
      createFile(runId,
                 pipelineId,
                 moduleId,
                 sampleId,
                 libId,
                 key,
                 path,
                 md5,
                 link,
                 size)
    else
      db.run(
        filter.update(
          Schema.File(runId,
                      pipelineId,
                      moduleId,
                      sampleId,
                      libId,
                      key,
                      path,
                      md5,
                      link,
                      size)))
  }

  /** Creates a exeutable. This method will raise expection if it already exist */
  def createExecutable(runId: Int,
                       toolName: String,
                       version: Option[String] = None,
                       path: Option[String] = None,
                       javaVersion: Option[String] = None,
                       exeMd5: Option[String] = None,
                       javaMd5: Option[String] = None,
                       jarPath: Option[String] = None): Future[Int] = {
    db.run(
      executables.forceInsert(
        Executable(runId,
                   toolName,
                   version,
                   path,
                   javaVersion,
                   exeMd5,
                   javaMd5,
                   jarPath)))
  }

  /** Create or update a [[Executable]] */
  def createOrUpdateExecutable(runId: Int,
                               toolName: String,
                               version: Option[String] = None,
                               path: Option[String] = None,
                               javaVersion: Option[String] = None,
                               exeMd5: Option[String] = None,
                               javaMd5: Option[String] = None,
                               jarPath: Option[String] = None): Future[Int] = {
    val filter = executablesFilter(Some(runId), Some(toolName))
    val r = Await.result(db.run(filter.size.result), Duration.Inf)
    if (r == 0)
      createExecutable(runId, toolName, version, javaVersion, exeMd5, javaMd5)
    else
      db.run(
        filter.update(
          Executable(runId,
                     toolName,
                     version,
                     path,
                     javaVersion,
                     exeMd5,
                     javaMd5,
                     jarPath)))
  }

}