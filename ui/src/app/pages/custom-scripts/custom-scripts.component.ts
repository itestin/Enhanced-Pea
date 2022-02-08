import {Component, OnInit} from '@angular/core'
import {Router} from '@angular/router'
import {TranslateService} from '@ngx-translate/core'
import {NzModalService, UploadChangeParam, UploadFile} from 'ng-zorro-antd'
import {ResourceService} from 'src/app/api/resource.service'
import {ResourceInfo, SimulationModel} from 'src/app/model/pea.model'
import {formatFileSize} from 'src/app/util/file'

@Component({
  selector: 'app-custom-scripts',
  templateUrl: './custom-scripts.component.html',
  styleUrls: ['./custom-scripts.component.css']
})
export class CustomScriptsComponent implements OnInit {

  UPLOAD_BASE_URL = '/api/scripts/upload'
  uploadUrl = ''
  fileList: UploadFile[] = []
  items: ResourceInfo[] = []
  breadcrumbItems: BreadcrumbPath[] = []
  path: string = ''
  newFolderVisible = false
  newFolderName = ''

  editVisible = false
  editFile = "edit file"
  editContent = ""

  constructor(
    private resourceService: ResourceService,
    private modalService: NzModalService,
    private i18nService: TranslateService,
  ) {
  }

  newFolder() {
    this.newFolderVisible = true
  }

  handleEditOk() {
    this.resourceService.modifyScript(this.editFile, this.editContent).subscribe(res => {
      this.editFile = ''
      this.editVisible = false
      this.editContent = ""
      this.loadFiles()
    })
  }

  handleEditCancel() {
    this.editFile = ''
    this.editVisible = false
    this.editContent = ""
  }

  edit(fileName: string) {
    // 打开浏览器修改的
    // if (this.editorBaseUrl) {
    //   const url = `${this.editorBaseUrl}${simulation.name.replace(/\./g, '/')}.scala`
    //   window.open(url)
    // }

    // 直接页面上修改
    this.resourceService.readScript(fileName).subscribe(res => {
      this.editContent = res.data
      this.editVisible = true
      this.editFile = fileName
    })
  }

  handleOk() {
    this.resourceService.newScriptFolder(this.path, this.newFolderName).subscribe(res => {
      this.newFolderName = ''
      this.newFolderVisible = false
      this.loadFiles()
    })
  }

  handleCancel() {
    this.newFolderName = ''
    this.newFolderVisible = false
  }

  uploadChange(param: UploadChangeParam) {
    if (param.file.status === 'done') {
      const done = this.fileList.filter(file => file.status === 'done').length
      if (done === this.fileList.length) {
        this.fileList = []
        this.loadFiles()
      }
    }
  }

  itemSize(item: ResourceInfo) {
    return formatFileSize(item.size)
  }

  itemColor(item: ResourceInfo) {
    if (item.isDirectory) {
      return '#1890ff'
    } else {
      return ''
    }
  }

  updateBreadcrumbItems() {
    const tmp: BreadcrumbPath[] = []
    this.path.split('/').forEach(value => {
      if (tmp.length === 0) {
        tmp.push({value: value, path: value})
      } else {
        tmp.push({value: value, path: `${tmp[tmp.length - 1].path}/${value}`})
      }
    })
    this.uploadUrl = `${this.UPLOAD_BASE_URL}?path=${this.path}`
    this.breadcrumbItems = tmp
  }

  click(item: ResourceInfo) {
    if (item.isDirectory) {
      if (this.path) {
        this.path = `${this.path}/${item.filename}`
      } else {
        this.path = item.filename
      }
      this.updateBreadcrumbItems()
      this.loadFiles()
    } else {
      // read file
      this.edit(`${this.path}/${item.filename}`)
    }
  }

  remove(item: ResourceInfo) {
    this.modalService.confirm({
      nzTitle: this.i18nService.instant('tips.deleteFile'),
      nzContent: item.filename,
      nzOnOk: () => {
        let path = item.filename
        if (this.path) {
          path = `${this.path}/${item.filename}`
        }
        this.resourceService.removeScript(path).subscribe(res => {
          this.loadFiles()
        })
      }
    })
  }

  download(item: ResourceInfo) {
    let path = item.filename
    if (this.path) {
      path = `${this.path}/${item.filename}`
    }
    this.resourceService.download(path, false)
  }

  downloadLink(item: ResourceInfo) {
    let path = item.filename
    if (this.path) {
      path = `${this.path}/${item.filename}`
    }
    return this.resourceService.downloadScript(path)
  }

  itemDate(item: ResourceInfo) {
    return new Date(item.modified).toLocaleString()
  }

  loadPath(path: string) {
    this.path = path
    this.updateBreadcrumbItems()
    this.loadFiles()
  }

  loadFiles() {
    this.resourceService.listScript(this.path).subscribe(res => {
      this.items = res.data
    })
  }

  ngOnInit() {
    this.updateBreadcrumbItems()
    this.loadFiles()
  }
}

interface BreadcrumbPath {
  value: string
  path: string
}
