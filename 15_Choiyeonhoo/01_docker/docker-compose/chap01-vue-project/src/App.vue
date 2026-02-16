<script setup>
import { ref, onMounted } from "vue";
import axios from 'axios';
import { getTodos, createTodo, deleteTodo, toggleTodo } from '@/api/todoApi';

const num1 = ref(0);
const num2 = ref(0);
const result = ref(0);

const sendPlus = async () => {
  const response = await axios.get(`http://localhost:8080/plus?num1=${num1.value}&num2=${num2.value}`);

  const data = response.data
  console.log(`data : `, data);
  result.value = data.sum;
}

/* todo 관련 기능 */
const todos = ref([]);
const newTodoTitle = ref('');

const loadTodos = async () => {
  try {
    todos.value = await getTodos();
  } catch (error) {
    console.error('Failed to load todos:', error);
  }
};

const addTodo = async () => {
  if (newTodoTitle.value.trim() === '') {
    return;
  }

  try {
    await createTodo(newTodoTitle.value);
    newTodoTitle.value = '';
    await loadTodos();
  } catch (error) {
    console.error('Failed to create todo:', error);
  }
};

const removeTodo = async (id) => {
  try {
    await deleteTodo(id);
    await loadTodos();
  } catch (error) {
    console.error('Failed to delete todo:', error);
  }
};

const toggleTodoStatus = async (id) => {
  try {
    await toggleTodo(id);
    await loadTodos();
  } catch (error) {
    console.error('Failed to toggle todo:', error);
  }
};

onMounted(() => {
  loadTodos();
});

</script>

<template>
  <div class="plus">
    <h1>덧셈 기능 만들기</h1>
    <label>num1 : </label>
    <input type="text" v-model="num1">&nbsp;
    <label>num2 : </label>
    <input type="text" v-model="num2">&nbsp;
    <button @click="sendPlus">더하기</button>
    <hr>
    <p>{{ num1 }} + {{ num2 }} = {{ result }}</p>
  </div>

<hr>
<hr>

<div class="todo-app">
<h1>TodoList</h1>

<div class="input-section">
  <input
      type="text"
      v-model="newTodoTitle"
      placeholder="What needs to be done?"
      @keyup.enter="addTodo"
  >
  <button @click="addTodo">Add</button>
</div>

<ul class="todo-list">
  <li v-for="todo in todos" :key="todo.id" class="todo-item">
    <input
        type="checkbox"
        :checked="todo.completed"
        @change="toggleTodoStatus(todo.id)"
    >
    <span :class="{ completed: todo.completed }">{{ todo.title }}</span>
    <button @click="removeTodo(todo.id)">Delete</button>
  </li>
</ul>
</div>
</template>
<style scoped>

.todo-app {
  max-width: 600px;
  margin: 50px auto;
  padding: 20px;
}

h1 {
  text-align: center;
  margin-bottom: 30px;
}

.input-section {
  display: flex;
  gap: 10px;
  margin-bottom: 20px;
}

.input-section input {
  flex: 1;
  padding: 8px 12px;
  font-size: 14px;
  border: 1px solid #ddd;
  border-radius: 4px;
}

.input-section button {
  padding: 8px 20px;
  font-size: 14px;
  background-color: #4CAF50;
  color: white;
  border: none;
  border-radius: 4px;
  cursor: pointer;
}

.input-section button:hover {
  background-color: #45a049;
}

.todo-list {
  list-style: none;
  padding: 0;
  margin: 0;
}

.todo-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px;
  border-bottom: 1px solid #eee;
}

.todo-item input[type="checkbox"] {
  cursor: pointer;
}

.todo-item span {
  flex: 1;
  font-size: 16px;
}

.todo-item span.completed {
  text-decoration: line-through;
  color: #999;
}

.todo-item button {
  padding: 5px 15px;
  font-size: 12px;
  background-color: #f44336;
  color: white;
  border: none;
  border-radius: 4px;
  cursor: pointer;
}

.todo-item button:hover {
  background-color: #da190b;
}

</style>
