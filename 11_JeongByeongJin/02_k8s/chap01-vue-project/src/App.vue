<script setup>
import { ref } from "vue";
import axios from 'axios';

const num1 = ref(0);
const num2 = ref(0);
const result = ref(0);

const sendPlus = async () => {
  /* NodePort에 설정된 30001번 포트 요청*/
  // const response = await axios.get(`http://localhost:30001/plus?num1=${num1.value}&num2=${num2.value}`);

  /* Ingress를 이용한 절대 경로 통신 ㅇ*/
  const response = await axios.get(`/boot/plus?num1=${num1.value}&num2=${num2.value}`);

  const data = response.data
  console.log(`data : `, data);
  result.value = data.sum;
}
</script>

<template>
  <div class="plus">
    <h1>덧셈 기능 만들기!@!@!@!@!@</h1>
    <label>num1 : </label>
    <input type="text" v-model="num1">&nbsp;
    <label>num2 : </label>
    <input type="text" v-model="num2">&nbsp;
    <button @click="sendPlus">더하기!!!!!!!!</button>
    <hr>
    <p>{{ num1 }} + {{ num2 }} = {{ result }}</p>
  </div>
</template>

<style scoped>

</style>
