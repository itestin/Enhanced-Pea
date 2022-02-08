import {HttpClient} from '@angular/common/http'
import {Injectable} from '@angular/core'

import {ApiRes} from '../model/api.model'
import {ResourceInfo} from '../model/pea.model'
import {BaseService} from './base.service'

@Injectable({
  providedIn: 'root'
})
export class ResourceService extends BaseService {

  API_BASE_RESOURCE = `${this.API_BASE}/resource`

  API_BASE_SCRIPT = `${this.API_BASE}/scripts`

  constructor(private http: HttpClient) {
    super()
  }

  //fileName参数名字要一致
  readFile(fileName: string) {
    return this.http.get<ApiRes<string>>(`${this.API_BASE_RESOURCE}/readFile?fileName=${fileName}`)
  }

  readScript(path: string) {
    return this.http.get<ApiRes<string>>(`${this.API_BASE_SCRIPT}/readScript?path=${path}`)
  }

  modifyFile(fileName: string, content: String) {
    return this.http.post<ApiRes<string>>(`${this.API_BASE_RESOURCE}/modify`, {fileName: fileName, content: content})
  }

  modifyScript(fileName: string, content: String) {
    return this.http.post<ApiRes<string>>(`${this.API_BASE_SCRIPT}/modify`, {fileName: fileName, content: content})
  }

  read1k(path: string, isLibs: boolean) {
    return this.http.get<ApiRes<string>>(`${this.API_BASE_RESOURCE}${isLibs ? '/jar' : ''}/read1k?path=${path}`)
  }

  readScript1k(path: string) {
    return this.http.get<ApiRes<string>>(`${this.API_BASE_SCRIPT}/read1k?path=${path}`)
  }

  list(file: string, isLibs: boolean) {
    return this.http.post<ApiRes<ResourceInfo[]>>(`${this.API_BASE_RESOURCE}${isLibs ? '/jar' : ''}/list`, {file: file || ''})
  }

  listScript(file: string) {
    return this.http.post<ApiRes<ResourceInfo[]>>(`${this.API_BASE_SCRIPT}/list`, {file: file || ''})
  }


  remove(file: string, isLibs: boolean) {
    return this.http.post<ApiRes<boolean>>(`${this.API_BASE_RESOURCE}${isLibs ? '/jar' : ''}/remove`, {file: file})
  }

  removeScript(file: string) {
    return this.http.post<ApiRes<boolean>>(`${this.API_BASE_SCRIPT}/remove`, {file: file})
  }

  newFolder(path: string, name: string, isLibs: boolean) {
    return this.http.put<ApiRes<boolean>>(`${this.API_BASE_RESOURCE}${isLibs ? '/jar' : ''}/folder`, {
      path: path,
      name: name
    })
  }

  newScriptFolder(path: string, name: string) {
    return this.http.put<ApiRes<boolean>>(`${this.API_BASE_SCRIPT}/folder`, {
      path: path,
      name: name
    })
  }


  download(path: string, isLibs: boolean) {
    const url = `${this.API_BASE_RESOURCE}${isLibs ? '/jar' : ''}/download?path=${path}`
    this.http.get<Blob>(url, {responseType: 'blob' as 'json'}).subscribe(res => {
      const link = window.URL.createObjectURL(res)
      window.open(link)
    })
  }

  downloadLink(path: string, isLibs: boolean) {
    return `${this.API_BASE_RESOURCE}${isLibs ? '/jar' : ''}/download?path=${path}`
  }

  downloadScript(path: string) {
    return `${this.API_BASE_SCRIPT}/download?path=${path}`
  }
}
