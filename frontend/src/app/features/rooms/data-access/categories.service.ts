import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, switchMap } from 'rxjs';
import { UserService } from '../../../core/user-service';
import { Category } from '../models/category.model';

@Injectable({ providedIn: 'root' })
export class CategoriesService {
  private readonly http = inject(HttpClient);
  private readonly userService = inject(UserService);

  private withCurrentUser<T>(request: () => Observable<T>): Observable<T> {
    return this.userService.syncUser().pipe(switchMap(() => request()));
  }

  listCategories(roomId: string): Observable<Category[]> {
    return this.withCurrentUser(() =>
      this.http.get<Category[]>(`/api/rooms/${roomId}/categories`)
    );
  }

  createCategory(roomId: string, title: string): Observable<Category> {
    return this.withCurrentUser(() =>
      this.http.post<Category>(`/api/rooms/${roomId}/categories`, { title })
    );
  }

  updateCategory(categoryId: number, title: string): Observable<Category> {
    return this.withCurrentUser(() =>
      this.http.put<Category>(`/api/categories/${categoryId}`, { title })
    );
  }

  deleteCategory(categoryId: number): Observable<void> {
    return this.withCurrentUser(() =>
      this.http.delete<void>(`/api/categories/${categoryId}`)
    );
  }
}