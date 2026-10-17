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


suspend fun prepareIngredient(ingredientName: String): String =
    withContext(Dispatchers.Default) {
        delay(1500)
        when (ingredientName) {
            "лук" -> "Нарезанный лук"
            "масло" -> "Растопленное масло"
            "сливки" -> "Взбитые    сливки"
            else -> "Подготовленный $ingredientName"
        }
    }
suspend fun cookOrder(order: Order) = coroutineScope {
    val cookingJob = launch {
        while (isActive && order.cookingProgress < 100) {
            delay(500)
            order.cookingProgress += 10
            order.status = "Готовится: ${order.cookingProgress}%"
            println("[${order.id}] ${order.dishName}: ${order.cookingProgress}%")
        }
        order.status = "Готово"
    }
    launch {
        while (isActive && order.cookingProgress < 100) {
            delay(2000)
            if (isActive) {
                println("[${order.id}] Температура в норме (термощуп)")
            }
        }
    }
    launch {
        var warned = false
        while (isActive && order.cookingProgress < 100) {
            delay(100)
            if (!warned && order.cookingProgress >= 80) {
                warned = true
                println("[${order.id}] ⚠ Блюдо почти готово, пора на финишную обработку")
            }
        }
    }
    cookingJob.join()
}
suspend fun checkDishQuality(orderId: String): Deferred<DishResult> =
    CoroutineScope(Dispatchers.Default).async {
        delay(3000)
        val score = Random.nextDouble(7.0, 10.0)
        DishResult(orderId, "Блюдо $orderId", score)
    }
suspend fun cancelOrder(order: Order, job: Job) {
    delay(2000)
    job.cancel()
    println("Заказ ${order.id} отменён, блюдо ${order.dishName} снято с приготовления")
}
suspend fun runKitchenChannel() = coroutineScope {
    val channel = Channel<DishResult>()
    launch {
        repeat(5) { i ->
            val result = DishResult("ORD-$i", "Блюдо $i", Random.nextDouble(7.0, 10.0))
            println("[Кухня] Отправлено: ${result.orderId}")
            channel.send(result)
            delay(300)
        }
        channel.close()
    }
    launch {
        for (result in channel) {
            println("[Метрдотель] Выдано: ${result.orderId} (${result.dishName}), оценка ${"%.2f".format(result.qualityScore)}")
        }
    }
}
suspend fun cookWithSupervisor() = coroutineScope {
    val supervisor = SupervisorJob()
    val scope = CoroutineScope(Dispatchers.Default + supervisor)
    scope.launch {
        delay(300)
        throw IllegalStateException("Блюдо подгорело!")
    }
    scope.launch {
        repeat(3) {
            delay(500)
            println("[Supervisor] Другое блюдо готовится, шаг $it")
        }
    }
    delay(2500)
    supervisor.cancel()
}
fun main() = runBlocking {
    println("=== 2. Подготовка ингредиентов ===")
    println(prepareIngredient("лук"))
    println(prepareIngredient("масло"))
    println(prepareIngredient("сливки"))
    println("\n=== 3. Готовка заказа ===")
    val order1 = Order("ORD-1", "Ризотто")
    cookOrder(order1)
    println("\n=== 4. Проверка качества (async/await) ===")
    val deferred = checkDishQuality("ORD-1")
    val result = deferred.await()
    println("Оценка качества: ${"%.2f".format(result.qualityScore)} для ${result.dishName}")
    println("\n=== 5. Отмена заказа ===")
    val order2 = Order("ORD-2", "Су-вид")
    val job = launch {
        cookOrder(order2)
    }
    cancelOrder(order2, job)
    job.join()
    println("Задача готовки активна? ${job.isActive}")
    println("\n=== 6. Канал кухня → раздача ===")
    runKitchenChannel()
    println("\n=== 7. Обработка исключений (SupervisorJob) ===")
    cookWithSupervisor()
    println("\n=== Все заказы обработаны ===")
}