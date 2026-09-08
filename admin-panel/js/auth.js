const Auth = {
  save(token, remember) {
    sessionStorage.removeItem('mdmesh.token');
    localStorage.removeItem('mdmesh.token');
    if (remember) localStorage.setItem('mdmesh.token', token);
    else sessionStorage.setItem('mdmesh.token', token);
  },
  token() {
    return sessionStorage.getItem('mdmesh.token') || localStorage.getItem('mdmesh.token');
  },
  clear() {
    sessionStorage.removeItem('mdmesh.token');
    localStorage.removeItem('mdmesh.token');
  },
  require() {
    if (!this.token()) {
      window.location.replace('./index.html');
      return false;
    }
    return true;
  }
};
