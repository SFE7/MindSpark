
import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';

@Injectable({
  providedIn: 'root'
})
export class BackendService {
  private http = inject(HttpClient);

  testBackend() {
    return this.http.get('/api/test', {
      responseType: 'text'
    });
  }
}
