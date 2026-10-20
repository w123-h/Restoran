import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlin.random.Random

data class Order(
    val id: String,
    val dishName: String,
    var cookingProgress: Int = 0,
    var status: String = "Ожидает"
)

data class DishResult(
    val orderId: String,
    val dishName: String,
    val qualityScore: Double
)

suspend fun prepareIngredient(ingredientName: String): String = withContext(Dispatchers.Default) {
    delay(1500) // имитация вычислительной работы (нарезка, взбивание)
    when (ingredientName.lowercase()) {
        "лук" -> "Нарезанный лук"
        "масло" -> "Растопленное масло"
        "сливки" -> "Взбитые сливки"
        else -> "Подготовленный $ingredientName"
    }
}

suspend fun cookOrder(order: Order) = coroutineScope {
    order.status = "Готовится"

    val cookingJob = launch {
        while (isActive && order.cookingProgress < 100) {
            delay(500)
            order.cookingProgress += 10
            println("[${order.id}] ${order.dishName}: ${order.cookingProgress}%")
        }
        if (isActive) {
            order.status = "Готово"
            println("[${order.id}] ${order.dishName} полностью готово!")
        }
    }

    // Контроль температуры
    launch {
        delay(2000)
        if (isActive) {
            println("[${order.id}] Температура ${order.dishName} в норме")
        }
    }

    // Мониторинг срока
    launch {
        while (isActive && order.cookingProgress < 80) {
            delay(300)
        }
        if (isActive && order.cookingProgress >= 80) {
            println("[${order.id}] Блюдо почти готово, пора на финишную обработку")
        }
    }

    cookingJob.join()
}

// 4. Вспомнить

suspend fun checkDishQuality(orderId: String): Deferred<DishResult> = coroutineScope {
    async {
        delay(3000) // дегустация шефом
        val score = Random.nextDouble(7.0, 10.0)
        DishResult(orderId, "Проверенное блюдо", score)
    }
}

suspend fun cancelOrder(order: Order, job: Job) {
    delay(2000)
    job.cancel()
    println("Заказ ${order.id} отменён, блюдо ${order.dishName} снято с приготовления")
}

suspend fun runKitchenChannel() = coroutineScope {
    val channel = Channel<DishResult>()

    // Производитель (кухня)
    launch {
        val dishes = listOf(
            DishResult("ORD-1", "Ризотто с трюфелем", 9.4),
            DishResult("ORD-2", "Стейк су-вид", 9.1),
            DishResult("ORD-3", "Утка конфи", 8.8),
            DishResult("ORD-4", "Суп-пюре", 9.7),
            DishResult("ORD-5", "Шоколадный фондан", 9.9)
        )
        for (dish in dishes) {
            channel.send(dish)
            println("Кухня → раздача: ${dish.dishName}")
            delay(400)
        }
        channel.close()
        println("Канал закрыт. Все блюда переданы.")
    }

    // Потребитель (метрдотель)
    launch {
        for (result in channel) {
            println("Метрдотель получил: ${result.dishName} (оценка ${"%.1f".format(result.qualityScore)})")
        }
        println("Раздача завершена. Сводка по выданным блюдам готова.")
    }
}

suspend fun cookWithExceptionHandling() = coroutineScope {
    val supervisor = SupervisorJob()
    val scope = CoroutineScope(coroutineContext + supervisor)

    val orders = listOf(
        Order("SAFE-1", "Ризотто"),
        Order("FAIL-2", "Подгоревший стейк"),
        Order("SAFE-3", "Суп")
    )

    orders.forEach { order ->
        scope.launch {
            if (order.id == "FAIL-2") {
                delay(800)
                throw RuntimeException("Блюдо ${order.dishName} подгорело! Прогресс пошёл в минус.")
            }
            // Нормальная готовка
            while (order.cookingProgress < 100) {
                delay(400)
                order.cookingProgress += 20
                println("[${order.id}] ${order.dishName}: ${order.cookingProgress}%")
            }
            println("[${order.id}] ${order.dishName} успешно готово")
        }
    }

    delay(3000) // даём время всем задачам
    supervisor.cancel()
    println("Смена продолжается — падение одного блюда не убило кухню")
}

fun main() = runBlocking {
    println("1. Модели данных")
    val order = Order("ORD-42", "Ризотто с белыми грибами")
    println(order)

    println("\n2. Подготовка ингредиентов (Dispatchers.Default)")
    val onion = prepareIngredient("лук")
    val cream = prepareIngredient("сливки")
    println("$onion, $cream")

    println("\n3. Параллельная готовка (coroutineScope + launch)")
    cookOrder(Order("ORD-100", "Су-вид лосось"))

    println("\n4. Async / await — проверка качества")
    val deferred = checkDishQuality("ORD-100")
    val result = deferred.await()
    println("Оценка качества: ${"%.1f".format(result.qualityScore)}")

    println("\n5. Отмена заказа")
    val cancelableOrder = Order("ORD-CANCEL", "Стейк")
    val job = launch {
        while (isActive && cancelableOrder.cookingProgress < 100) {
            delay(500)
            cancelableOrder.cookingProgress += 10
            println("[${cancelableOrder.id}] прогресс: ${cancelableOrder.cookingProgress}%")
        }
        if (!isActive) println("[${cancelableOrder.id}] готовка корректно остановлена (isActive=false)")
    }
    cancelOrder(cancelableOrder, job)
    job.join()

    println("\n6. Каналы (Channel)")
    runKitchenChannel()

    println("\n7. SupervisorJob — падение одного не валит всех")
    cookWithExceptionHandling()

    println("\nКухня «Параллель» закрыта. Все процессы отработали")
}