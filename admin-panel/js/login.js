if (Auth.token()) {
  window.location.replace('./app.html');
}

const form = document.getElementById('login-form');
const errorEl = document.getElementById('login-error');
const btn = document.getElementById('login-btn');

form.addEventListener('submit', async (event) => {
  event.preventDefault();
  errorEl.textContent = '';
  const username = form.username.value.trim();
  const password = form.password.value;
  if (username.length < 3) {
    errorEl.textContent = 'Username must be at least 3 characters.';
    return;
  }
  if (password.length < 6) {
    errorEl.textContent = 'Password must be at least 6 characters.';
    return;
  }
  btn.disabled = true;
  try {
    const result = await Api.login(username, password);
    Auth.save(result.token, document.getElementById('remember').checked);
    window.location.replace('./app.html');
  } catch (error) {
    errorEl.textContent = error.message || 'Invalid username or password';
  } finally {
    btn.disabled = false;
  }
});
