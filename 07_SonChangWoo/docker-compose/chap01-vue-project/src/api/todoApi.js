import axios from 'axios';

const API_BASE_URL = 'http://localhost:8080/api/todos';

export const getTodos = async () => {
    const response = await axios.get(API_BASE_URL);
    return response.data;
};

export const createTodo = async (title) => {
    const response = await axios.post(API_BASE_URL, { title });
    return response.data;
};

export const deleteTodo = async (id) => {
    await axios.delete(`${API_BASE_URL}/${id}`);
};

export const toggleTodo = async (id) => {
    const response = await axios.patch(`${API_BASE_URL}/${id}/toggle`);
    return response.data;
};