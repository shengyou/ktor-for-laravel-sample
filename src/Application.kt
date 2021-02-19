package io.kraftsman

import io.ktor.application.*
import io.ktor.response.*
import io.ktor.request.*
import io.ktor.routing.*
import io.ktor.http.*
import com.fasterxml.jackson.databind.*
import io.ktor.jackson.*
import io.ktor.features.*
import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.exposed.sql.transactions.transaction
import java.sql.Connection

fun main(args: Array<String>): Unit = io.ktor.server.netty.EngineMain.main(args)

@Suppress("unused") // Referenced in application.conf
@kotlin.jvm.JvmOverloads
fun Application.module(testing: Boolean = false) {
    install(ContentNegotiation) {
        jackson {
            enable(SerializationFeature.INDENT_OUTPUT)
        }
    }

    Database.connect(
        url = "jdbc:sqlite:./database/database.sqlite",
//        url = "jdbc:sqlite::memory:",
        driver = "org.sqlite.JDBC"
    )

    TransactionManager.manager.defaultIsolationLevel = Connection.TRANSACTION_SERIALIZABLE

    transaction {
        SchemaUtils.create(Tasks)
    }

    routing {
        get("/") {
            call.respondText("HELLO WORLD!", contentType = ContentType.Text.Plain)
        }

        get("/json/jackson") {
            call.respond(mapOf("hello" to "world"))
        }

        get("/api/tasks") {
            val tasks = transaction {
                Task.all()
                    .orderBy(Tasks.id to SortOrder.DESC)
                    .map {
                        TaskDto(it.id.value, it.title, it.completed)
                    }
            }

            call.respond(mapOf("data" to tasks))
        }

        post("/api/tasks") {
            val taskDto = call.receive<TaskDto>()
            transaction {
                Task.new {
                    title = taskDto.title
                }
            }
            call.respond(HttpStatusCode.Created)
        }

        get("/api/tasks/{id}") {
            val id = call.parameters["id"]?.toIntOrNull()

            if (id == null) {
                call.respond(HttpStatusCode.BadRequest)
            }

            val task = transaction {
                Task.findById(id!!)?.let {
                    TaskDto(it.id.value, it.title, it.completed)
                }
            }

            if (task == null) {
                call.respond(HttpStatusCode.NotFound)
            }

            call.respond(task!!)
        }

        patch("/api/tasks") {
            val dto = call.receive<TaskDto>()

            if (dto.id == null) {
                call.respond(HttpStatusCode.BadRequest)
            }

            transaction {
                val task = Task.findById(dto.id!!)
                task?.title = dto.title
                task?.completed = dto.completed
            }

            call.respond(HttpStatusCode.NoContent)
        }

        delete("/api/tasks") {
            val dto = call.receive<TaskDto>()

            if (dto.id == null) {
                call.respond(HttpStatusCode.BadRequest)
            }

            transaction {
                val task = Task.findById(dto.id!!)
                task?.delete()
            }

            call.respond(HttpStatusCode.NoContent)
        }
    }
}

object Tasks : IntIdTable("tasks") {
    val title = varchar("title", 255)
    val completed = bool("completed").default(false)
}

class Task(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<Task>(Tasks)

    var title by Tasks.title
    var completed by Tasks.completed
}

data class TaskDto(val id: Int?, val title: String, val completed: Boolean = false)
